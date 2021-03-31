/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.integration;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.test.rest.ESRestTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class MlRestTestStateCleaner {

    private static final Set<String> NOT_DELETED_TRAINED_MODELS = Collections.singleton("lang_ident_model_1");
    private final Logger logger;
    private final RestClient adminClient;

    public MlRestTestStateCleaner(Logger logger, RestClient adminClient) {
        this.logger = logger;
        this.adminClient = adminClient;
    }

    public void clearMlMetadata() throws IOException {
        deleteAllTrainedModels();
        deleteAllDatafeeds();
        deleteAllJobs();
        deleteAllDataFrameAnalytics();
        // indices will be deleted by the ESRestTestCase class
    }

    @SuppressWarnings("unchecked")
    private void deleteAllTrainedModels() throws IOException {
        final Request getAllTrainedModelStats = new Request("GET", "/_ml/trained_models/_stats");
        getAllTrainedModelStats.addParameter("size", "10000");
        final Response trainedModelsStatsResponse = adminClient.performRequest(getAllTrainedModelStats);

        final List<Map<String, Object>> pipelines = (List<Map<String, Object>>) XContentMapValues.extractValue(
            "trained_model_stats.ingest.pipelines",
            ESRestTestCase.entityAsMap(trainedModelsStatsResponse)
        );
        Set<String> pipelineIds = pipelines.stream().flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
        for (String pipelineId : pipelineIds) {
            try {
                adminClient.performRequest(new Request("DELETE", "/_ingest/pipeline/" + pipelineId));
            } catch (Exception ex) {
                logger.warn(() -> new ParameterizedMessage("failed to delete pipeline [{}]", pipelineId), ex);
            }
        }

        final Request getTrainedModels = new Request("GET", "/_ml/trained_models");
        getTrainedModels.addParameter("size", "10000");
        final Response trainedModelsResponse = adminClient.performRequest(getTrainedModels);
        final List<Map<String, Object>> models = (List<Map<String, Object>>) XContentMapValues.extractValue(
            "trained_model_configs",
            ESRestTestCase.entityAsMap(trainedModelsResponse)
        );
        if (models == null || models.isEmpty()) {
            return;
        }
        for (Map<String, Object> model : models) {
            String modelId = (String) model.get("model_id");
            if (NOT_DELETED_TRAINED_MODELS.contains(modelId)) {
                continue;
            }
            adminClient.performRequest(new Request("DELETE", "/_ml/trained_models/" + modelId));
        }
    }

    @SuppressWarnings("unchecked")
    private void deleteAllDatafeeds() throws IOException {
        final Request datafeedsRequest = new Request("GET", "/_ml/datafeeds");
        datafeedsRequest.addParameter("filter_path", "datafeeds");
        final Response datafeedsResponse = adminClient.performRequest(datafeedsRequest);
        final List<Map<String, Object>> datafeeds =
                (List<Map<String, Object>>) XContentMapValues.extractValue("datafeeds", ESRestTestCase.entityAsMap(datafeedsResponse));
        if (datafeeds == null) {
            return;
        }

        try {
            adminClient.performRequest(new Request("POST", "/_ml/datafeeds/_all/_stop"));
        } catch (Exception e1) {
            logger.warn("failed to stop all datafeeds. Forcing stop", e1);
            try {
                adminClient.performRequest(new Request("POST", "/_ml/datafeeds/_all/_stop?force=true"));
            } catch (Exception e2) {
                logger.warn("Force-closing all data feeds failed", e2);
            }
            throw new RuntimeException(
                    "Had to resort to force-stopping datafeeds, something went wrong?", e1);
        }

        for (Map<String, Object> datafeed : datafeeds) {
            String datafeedId = (String) datafeed.get("datafeed_id");
            adminClient.performRequest(new Request("DELETE", "/_ml/datafeeds/" + datafeedId));
        }
    }

    private void deleteAllJobs() throws IOException {
        final Request jobsRequest = new Request("GET", "/_ml/anomaly_detectors");
        jobsRequest.addParameter("filter_path", "jobs");
        final Response response = adminClient.performRequest(jobsRequest);
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> jobConfigs =
                (List<Map<String, Object>>) XContentMapValues.extractValue("jobs", ESRestTestCase.entityAsMap(response));
        if (jobConfigs == null) {
            return;
        }

        try {
            adminClient.performRequest(new Request("POST", "/_ml/anomaly_detectors/_all/_close"));
        } catch (Exception e1) {
            logger.warn("failed to close all jobs. Forcing closed", e1);
            try {
                adminClient.performRequest(new Request("POST", "/_ml/anomaly_detectors/_all/_close?force=true"));
            } catch (Exception e2) {
                logger.warn("Force-closing all jobs failed", e2);
            }
            throw new RuntimeException("Had to resort to force-closing jobs, something went wrong?",
                    e1);
        }

        for (Map<String, Object> jobConfig : jobConfigs) {
            String jobId = (String) jobConfig.get("job_id");
            adminClient.performRequest(new Request("DELETE", "/_ml/anomaly_detectors/" + jobId));
        }
    }

    @SuppressWarnings({"unchecked"})
    private void deleteAllDataFrameAnalytics() throws IOException {
        stopAllDataFrameAnalytics();

        final Request analyticsRequest = new Request("GET", "/_ml/data_frame/analytics?size=10000");
        analyticsRequest.addParameter("filter_path", "data_frame_analytics");
        final Response analyticsResponse = adminClient.performRequest(analyticsRequest);
        List<Map<String, Object>> analytics = (List<Map<String, Object>>) XContentMapValues.extractValue(
            "data_frame_analytics", ESRestTestCase.entityAsMap(analyticsResponse));
        if (analytics == null) {
            return;
        }

        for (Map<String, Object> config : analytics) {
            String id = (String) config.get("id");
            adminClient.performRequest(new Request("DELETE", "/_ml/data_frame/analytics/" + id));
        }
    }

    private void stopAllDataFrameAnalytics() {
        try {
            adminClient.performRequest(new Request("POST", "_ml/data_frame/analytics/*/_stop"));
        } catch (Exception e1) {
            logger.warn("failed to stop all data frame analytics. Will proceed to force-stopping", e1);
            try {
                adminClient.performRequest(new Request("POST", "_ml/data_frame/analytics/*/_stop?force=true"));
            } catch (Exception e2) {
                logger.warn("Force-stopping all data frame analytics failed", e2);
            }
            throw new RuntimeException("Had to resort to force-stopping data frame analytics, something went wrong?", e1);
        }
    }
}
