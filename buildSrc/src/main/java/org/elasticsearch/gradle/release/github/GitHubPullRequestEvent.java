/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GitHubPullRequestEvent {
    private int number;

    @JsonProperty("pull_request")
    private GitHubPullRequest pullRequest;

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public GitHubPullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(GitHubPullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    @Override
    public String toString() {
        return "GitHubPullRequestEvent{" + "number=" + number + ", pullRequest=" + pullRequest + '}';
    }
}
