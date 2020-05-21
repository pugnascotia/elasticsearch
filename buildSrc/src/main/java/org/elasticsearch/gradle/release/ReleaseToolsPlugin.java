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

package org.elasticsearch.gradle.release;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ReleaseToolsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new IllegalStateException(this.getClass().getName() + " can only be applied to the root project.");
        }

        project.getTasks()
            .register("buildReleaseNotes", GenerateReleaseNotesTask.class)
            .configure(action -> action.setDescription("Generates release notes from issue and PR information in GitHub"));

        project.getTasks()
            .register("relabelGithubIssues", RelabelGitHubIssuesTask.class)
            .configure(action -> action.setDescription("Adds and/or removes labels from issues and PRs"));
    }
}
