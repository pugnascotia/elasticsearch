/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.github;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * This plugin defines tasks related to working with PRs in GitHub.
 */
public class GitHubActionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks()
            .register("checkPrLabels", CheckPrLabelsTask.class, task -> {
                task.setGroup("GitHub");
                task.setDescription("Checks that a PR has all the required labels");
            });

        project.getTasks()
            .register("generateChangelogFile", GenerateChangelogFileTask.class, task -> {
                task.setGroup("GitHub");
                task.setDescription("Generates or updates a changelog file for a PR");

                task.getChangelogDir().set(project.getLayout().getProjectDirectory().dir("docs/changelog"));
            });
    }
}
