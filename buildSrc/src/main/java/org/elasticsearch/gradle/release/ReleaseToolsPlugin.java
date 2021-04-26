/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release;

import org.elasticsearch.gradle.Version;
import org.elasticsearch.gradle.VersionProperties;
import org.elasticsearch.gradle.internal.precommit.ValidateYamlAgainstSchemaTask;
import org.elasticsearch.gradle.precommit.PrecommitTaskPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;

import javax.inject.Inject;
import java.io.File;

/**
 * This plugin defines tasks related to releasing Elasticsearch.
 */
public class ReleaseToolsPlugin implements Plugin<Project> {

    private final ProjectLayout projectLayout;

    @Inject
    public ReleaseToolsPlugin(ProjectLayout projectLayout) {
        this.projectLayout = projectLayout;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(PrecommitTaskPlugin.class);

        final FileTree yamlFiles = projectLayout.getProjectDirectory()
            .dir("docs/changelog")
            .getAsFileTree()
            .matching(new PatternSet().include("**/*.yml", "**/*.yaml"));

        final Provider<ValidateYamlAgainstSchemaTask> validateChangelogsAgainstYamlTask = project.getTasks()
            .register("validateChangelogsAgainstSchema", ValidateYamlAgainstSchemaTask.class, task -> {
                task.setGroup("Documentation");
                task.setDescription("Validates that all the changelog YAML files comply with the changelog schema");
                task.setInputFiles(yamlFiles);
                task.setJsonSchema(new File(project.getRootDir(), "buildSrc/src/main/resources/changelog-schema.json"));
                task.setReport(new File(project.getBuildDir(), "reports/validateYaml.txt"));
            });

        final TaskProvider<ValidateYamlTask> validateChangelogsTask = project.getTasks()
            .register("validateChangelogs", ValidateYamlTask.class, task -> {
            task.setGroup("Documentation");
            task.setDescription("Validates that all the changelog YAML files are well-formed");
            task.setChangelogs(yamlFiles);
            task.dependsOn(validateChangelogsAgainstYamlTask);
        });

        project.getTasks().register("generateReleaseNotes", GenerateReleaseNotesTask.class).configure(task -> {
            final Version version = VersionProperties.getElasticsearchVersion();

            task.setGroup("Documentation");
            task.setDescription("Generates release notes from changelog files held in this checkout");
            task.setChangelogs(yamlFiles);
            task.setReleaseNotesIndexFile(projectLayout.getProjectDirectory().file("docs/reference/release-notes.asciidoc"));

            task.setReleaseNotesFile(
                projectLayout.getProjectDirectory()
                    .file(String.format("docs/reference/release-notes/%d.%d.asciidoc", version.getMajor(), version.getMinor()))
            );

            task.setReleaseHighlightsFile(projectLayout.getProjectDirectory().file("docs/reference/release-notes/highlights.asciidoc"));

            task.setBreakingChangesFile(
                projectLayout.getProjectDirectory()
                    .file(String.format("docs/reference/migration/migrate_%d_%d.asciidoc", version.getMajor(), version.getMinor()))
            );

            task.dependsOn(validateChangelogsTask);
        });

        project.getTasks().register("generateChangelogEntry", GenerateChangelogEntryTask.class);

        project.getTasks().named("precommit").configure(task -> task.dependsOn(validateChangelogsTask));
    }
}
