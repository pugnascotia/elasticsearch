/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.github;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.Set;

class GitHubUtils {
    private static final Logger LOGGER = Logging.getLogger(GitHubUtils.class);

    static int getPrNumber() {
        final String githubRef = System.getenv("GITHUB_REF");

        if (githubRef == null) {
            throw new GradleException("GITHUB_REF not defined in environment");
        }

        if (githubRef.matches("^refs/pull/\\d+/merge$") == false) {
            throw new GradleException("Expected GITHUB_REF to match regex [^refs/pull/\\d+/merge$] but was [" + githubRef + "]");
        }

        return Integer.parseInt(githubRef.split("/")[2]);
    }

    static boolean shouldSkipPr(Set<String> exclusionLabels, GHPullRequest pullRequest, Set<String> labels) throws IOException {
        if (pullRequest.isDraft()) {
            LOGGER.info("PR #{} is a draft, skipping", pullRequest.getNumber());
            return true;
        }

        for (String exclusionLabel : exclusionLabels) {
            if (labels.contains(exclusionLabel)) {
                LOGGER.info("PR #{} is labelled [{}], skipping", pullRequest.getNumber(), exclusionLabel);
                return true;
            }
        }

        return false;
    }
}
