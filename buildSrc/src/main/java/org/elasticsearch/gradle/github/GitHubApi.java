/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.gradle.github;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class encapsulates some of the cumbersome details of making calls to the GitHub v3 API.
 * It doesn't attempt to model the endpoints and payloads, it just makes it easy to perform the
 * HTTP calls, and automatically parses the JSON responses for GET requests. It does contain
 * a couple of helpers for some common tasks.
 */
class GitHubApi {
    private static final Logger LOGGER = Logging.getLogger(GitHubApi.class);

    private final GHRepository repository;

    private final String repositoryName;
    private final GitHub github;

    private static String getRepository() {
        /* This is the repository to access in the usual <code>user/repo</code> format */
        String repository = System.getenv("GITHUB_REPOSITORY");

        if (repository == null) {
            throw new GradleException("GITHUB_REPOSITORY environment variable not defined");
        }

        repository = repository.trim();

        if (repository.isEmpty()) {
            throw new GradleException("GITHUB_REPOSITORY environment variable cannot be empty");
        }

        return repository;
    }

    GitHubApi() throws IOException {
        this(getRepository());
    }

    GitHubApi(String repository) throws IOException {
        this.repositoryName = repository;

        this.github = new GitHubBuilder().withOAuthToken(loadGitHubKey()).build();

        this.repository = github.getRepository(repository);
    }

    public boolean isMemberOfTeam(GHUser user) throws IOException {
        final GHOrganization elastic = this.github.getOrganization("elastic");
        return elastic.getTeamByName("elasticsearch-team").hasMember(user);
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public GHPullRequest fetchPullRequest(int prNumber) throws IOException {
        return this.repository.getPullRequest(prNumber);
    }

    /**
     * Fetches the user's GitHub Personal Access token, either via the <code>GITHUB_TOKEN</code> environment variable
     * or from disk, specifically from <code>$HOME/.elastic/github.token</code>
     */
    private String loadGitHubKey() throws IOException {
        String overrideKey = System.getenv("GITHUB_TOKEN");

        if (overrideKey != null) {
            overrideKey = overrideKey.trim();
            if (overrideKey.isEmpty() == false) {
                LOGGER.debug("Using credentials from env var GITHUB_TOKEN");
                return overrideKey;
            }
        }

        String keyLocation = System.getenv("GITHUB_TOKEN_FILE");
        if (keyLocation == null) {
            keyLocation = System.getenv("HOME") + "/.elastic/github.token";
        }

        Path keyPath = Path.of(keyLocation);

        LOGGER.debug("Attempting to load API key from {}", keyPath);
        if (Files.notExists(keyPath)) {
            throw new GradleException(
                "File " + keyPath + " doesn't exist. Generate a Personal Access Token at https://github.com/settings/applications"
            );
        }

        final String keyString = Files.readString(keyPath).trim();

        if (keyString.matches("^[0-9a-fA-F]{40}$") == false) {
            throw new GradleException("Invalid GitHub key - expected 40 hexadecimal characters");
        }

        return keyString;
    }
}
