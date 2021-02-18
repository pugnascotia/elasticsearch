/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.github;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CheckPrLabelsTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(CheckPrLabelsTask.class);

    private static final Pattern VERSION = Pattern.compile(
        "(\\d+)\\.(\\d+)\\.(\\d+)(-alpha\\d+|-beta\\d+|-rc\\d+)?(-SNAPSHOT)?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> EXCLUSION_LABELS = Set.of("WIP");

    @TaskAction
    public void executeTask() throws IOException {
        final int prNumber = GitHubUtils.getPrNumber();

        final GitHubApi api = new GitHubApi();

        final GHPullRequest pullRequest = api.fetchPullRequest(prNumber);
        final Set<String> labels = pullRequest.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());

        if (GitHubUtils.shouldSkipPr(EXCLUSION_LABELS, pullRequest, labels)) {
            return;
        }

        final List<String> labelsToAdd = addLabels(api, pullRequest, labels);

        if (labelsToAdd.isEmpty() == false) {
            LOGGER.info("Adding labels to PR #{}: {}", pullRequest.getNumber(), labelsToAdd);
            // This method call will modify the PR. Note that it doesn't modify the state of `pullRequest`.
            pullRequest.addLabels(labelsToAdd.toArray(new String[] {}));
        }

        final List<String> errors = checkForRequiredLabels(labels);

        if (errors.isEmpty() == false) {
            StringJoiner message = new StringJoiner("\n");
            message.add("There are problems with PR #" + prNumber);
            for (String error : errors) {
                message.add("  - " + error);
            }

            throw new GradleException(message.toString());
        }
    }

    private List<String> addLabels(GitHubApi api, GHPullRequest pullRequest, Set<String> labels) throws IOException {
        List<String> labelsToAdd = new ArrayList<>();

        if (labels.contains("release highlight") && labels.contains(">docs") == false) {
            labelsToAdd.add(">docs");
        }

        if (api.isMemberOfTeam(pullRequest.getUser()) == false) {
            labelsToAdd.add("contributor");
        }

        return labelsToAdd;
    }

    private List<String> checkForRequiredLabels(Set<String> labels) {
        final List<String> errors = new ArrayList<>();

        if (labels.stream().noneMatch(l -> VERSION.matcher(l).find())) {
            errors.add("At least one version label is required");
        }

        if (labels.stream().noneMatch(l -> l.startsWith(":"))) {
            errors.add("At least one team label (starting with ':') is required");
        }

        if (labels.stream().noneMatch(l -> l.startsWith(">"))) {
            errors.add("At least one change type label (starting with '>') is required");
        }

        return errors;
    }
}
