package org.elasticsearch.common.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.Strings.isNullOrEmpty;

public class DeprecationIndexer implements ClusterStateListener {
    private static final Logger logger = LogManager.getLogger(DeprecationIndexer.class);

    private static final String TEMPLATE_NAME = ".deprecation_logs";

    private final ClusterService clusterService;
    private final NodeClient nodeClient;

    private boolean isTemplateCreated = false;

    public DeprecationIndexer(ClusterService clusterService, NodeClient nodeClient) {
        this.clusterService = clusterService;
        this.nodeClient = nodeClient;

        clusterService.addListener(this);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        ensureIndexTemplate();
        clusterService.removeListener(this);
    }

    private void ensureIndexTemplate() {
        PutIndexTemplateRequest putRequest = new PutIndexTemplateRequest(TEMPLATE_NAME);
        putRequest.patterns(List.of(TEMPLATE_NAME + ".*"));
        putRequest.create(true);
        putRequest.settings(Map.of("number_of_shards", 1));

        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.startObject()
                .startObject("properties")
                .field("@timestamp", Map.of("type", "date"))
                .field("message", Map.of("type", "text"))
                .field("keys", Map.of("type", "keyword"))
                .field("x-opaque-id", Map.of("type", "keyword"))
                .field("params", Map.of("type", "keyword"))
                .endObject()
                .endObject();
            putRequest.mapping(builder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        nodeClient.admin().indices().putTemplate(putRequest, new ActionListener<>() {
            @Override
            public void onResponse(AcknowledgedResponse acknowledgedResponse) {
                isTemplateCreated = true;
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof IllegalArgumentException && e.getMessage().contains("already exists")) {
                    // Template already exists, that's OK
                    isTemplateCreated = true;
                } else {
                    logger.error("Failed to create index template [" + TEMPLATE_NAME + "]", e);
                }
            }
        });
    }

    /**
     * Records a deprecation message to the `.deprecations` index.
     *
     * @param key       the key that was used to determine if this deprecation should have been be logged.
     *                  This is potentially useful when aggregating the recorded messages.
     * @param message   the message to log
     * @param xOpaqueId the associated "X-Opaque-ID" header value, if any
     * @param params    parameters to the message, if any
     */
    public void indexDeprecationMessage(String key, String message, String xOpaqueId, Object[] params) {
        if (isTemplateCreated == false) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();

        // ECS fields
        payload.put("@timestamp", Instant.now().toString());
        payload.put("message", message);
        if (isNullOrEmpty(key) == false) {
            payload.put("tags", key);
        }

        // Other fields
        if (isNullOrEmpty(xOpaqueId) == false) {
            // I considered putting this under labels.x-opaque-id, per ECS,
            // but wondered if that was a stretch? Also it may have high
            // cardinality, meaning that describing it as a label might
            // be a stretch.
            payload.put("x-opaque-id", xOpaqueId);
        }
        if (params != null && params.length > 0) {
            payload.put("params", params);
        }

        final String indexName = TEMPLATE_NAME + "." + DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now());

        new IndexRequestBuilder(nodeClient, IndexAction.INSTANCE).setIndex(indexName)
            .setOpType(DocWriteRequest.OpType.CREATE)
            .setSource(payload)
            .execute(new ActionListener<>() {
                @Override
                public void onResponse(IndexResponse indexResponse) {
                    // Nothing to do
                }

                @Override
                public void onFailure(Exception e) {
                    logger.error("Failed to index deprecation message", e);
                }
            });
    }
}
