/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GenerateChangelogEntryTask extends DefaultTask {

    private String eventPayload;

    @Input
    public String getEventPayload() {
        return eventPayload;
    }

    @Option(option = "event-payload", description = "Path to the GitHub event payload")
    public void setEventPayload(String eventPayload) {
        this.eventPayload = eventPayload;
    }

    @TaskAction
    public void executeTask() throws IOException {
        final Path eventPayloadPath = Path.of(this.eventPayload);

        if (Files.exists(eventPayloadPath) == false) {
            throw new GradleException("Invalid --event-payload argument [" + this.eventPayload + "]: does not exist");
        }

        if (Files.isRegularFile(eventPayloadPath) == false) {
            throw new GradleException("Invalid --event-payload argument [" + this.eventPayload + "]: not a regular file");
        }

        final byte[] payloadBytes = Files.readAllBytes(eventPayloadPath);

        final JsonNode jsonNode = new ObjectMapper().readTree(payloadBytes);

        final int prNumber = jsonNode.get("number").asInt();

        Files.writeString(Path.of("docs/changelog/" + prNumber + ".yaml"), "hello: world\n");
    }
}
