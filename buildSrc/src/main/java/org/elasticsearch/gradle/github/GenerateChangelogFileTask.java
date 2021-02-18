/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.github;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.elasticsearch.gradle.release.ChangelogEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GenerateChangelogFileTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(GenerateChangelogFileTask.class);

    private static final Pattern VERSION = Pattern.compile(
        "(\\d+)\\.(\\d+)\\.(\\d+)(-alpha\\d+|-beta\\d+|-rc\\d+)?(-SNAPSHOT)?",
        Pattern.CASE_INSENSITIVE
    );

    private final DirectoryProperty changelogDir = getProject().getObjects().directoryProperty();

    @InputDirectory
    public DirectoryProperty getChangelogDir() {
        return changelogDir;
    }

    private static final Set<String> EXCLUSION_LABELS = Set.of(
        ">non-issue",
        ">refactoring",
        ">docs",
        ">test",
        ">test-failure",
        ">test-mute",
        ":Delivery/Build",
        ":Delivery/Cloud",
        ":Delivery/Tooling",
        "backport",
        "WIP"
    );

    @TaskAction
    public void executeTask() throws IOException {
        final int prNumber = GitHubUtils.getPrNumber();

        final File changelogFile = this.changelogDir.file(prNumber + ".yaml").get().getAsFile();

        if (changelogFile.exists()) {
            return;
        }

        final GitHubApi api = new GitHubApi();

        final GHPullRequest pullRequest = api.fetchPullRequest(prNumber);
        final Set<String> labels = pullRequest.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());

        if (GitHubUtils.shouldSkipPr(EXCLUSION_LABELS, pullRequest, labels)) {
            return;
        }

        final ChangelogEntry changelog = new ChangelogEntry();

        changelog.setPr(prNumber);
        changelog.setSummary(pullRequest.getTitle());
        changelog.setVersions(labels.stream().filter(l -> VERSION.matcher(l).find()).collect(Collectors.toList()));
        changelog.setIssues(getIssues(api, pullRequest));
        changelog.setType(getType(labels));
        changelog.setArea(getArea(labels));

        if (labels.contains(">breaking") || labels.contains(">breaking-java")) {
            changelog.setBreaking(getBreaking(pullRequest, labels));
        }

        if (labels.contains("release highlight")) {
            changelog.setHighlight(getHighlight(pullRequest));
        }

        generateChangelogFile(changelogFile, changelog);
    }

    private String getType(Set<String> labels) {
        final List<String> types = labels.stream().filter(l -> l.startsWith(">")).map(l -> l.substring(1)).collect(Collectors.toList());

        return types.isEmpty() ? "Unknown" : String.join(", ", types);
    }

    private String getArea(Set<String> labels) {
        List<String> areaLabels = labels.stream().filter(l -> l.startsWith(":")).map(l -> {
            l = l.substring(1);
            if (l.contains("/")) {
                l = l.substring(l.indexOf("/") + 1);
            }
            return l;
        }).collect(Collectors.toList());

        final Map<String, String> areaOverrides = Map.of("ml", "Machine Learning", "Beats", "Beats Plugin", "Docs", "Docs Infrastructure");

        for (Map.Entry<String, String> entry : areaOverrides.entrySet()) {
            if (areaLabels.contains(entry.getValue())) {
                areaLabels = List.of(entry.getValue());
                break;
            }
        }

        if (areaLabels.isEmpty()) {
            areaLabels = List.of("Unknown");
        }

        return String.join(", ", areaLabels);
    }

    private void generateChangelogFile(File changelogFile, ChangelogEntry changelog) throws IOException {
        final YAMLFactory yamlFactory = new YAMLFactory();

        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)   // Removes quotes from strings
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)  // Gets rid of -- at the start of the file.
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS);           // Enables indentation of arrays

        final ObjectMapper mapper = new ObjectMapper(yamlFactory);
        // Keys in the YAML output will appear in the order that the fields are defined in the class.
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        // Indent the output
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        // Omit keys for null values
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        mapper.writeValue(changelogFile, changelog);
    }

    private ChangelogEntry.Breaking getBreaking(GHPullRequest pullRequest, Set<String> labels) {
        final ChangelogEntry.Breaking breaking = new ChangelogEntry.Breaking();

        final String[] titleWords = pullRequest.getTitle().replaceAll("[^a-zA-Z0-9]+", " ").toLowerCase(Locale.ROOT).split("\\s+");
        final String anchor = Arrays.stream(titleWords)
            .filter(word -> Set.of("the", "a", "an", "now").contains(word) == false)
            .collect(Collectors.joining("-"));

        breaking.setTitle(pullRequest.getTitle());
        breaking.setImpact("TODO");
        breaking.setDetails("TODO");
        breaking.setAnchor(anchor);
        breaking.setArea(getBreakingArea(labels));
        return breaking;
    }

    private ChangelogEntry.Highlight getHighlight(GHPullRequest pullRequest) {
        final ChangelogEntry.Highlight highlight = new ChangelogEntry.Highlight();

        highlight.setTitle(pullRequest.getTitle());
        highlight.setBody(pullRequest.getBody());

        return highlight;
    }

    private List<Integer> getIssues(GitHubApi api, GHPullRequest pullRequest) {
        final Pattern referencedIssues = Pattern.compile("(?:#|" + api.getRepositoryName() + "/issues/)(\\d+)");

        // I don't actually know if body can be null?
        final String body = Objects.requireNonNullElse(pullRequest.getBody(), "");
        final Matcher matcher = referencedIssues.matcher(body);

        List<Integer> issues = new ArrayList<>();

        while (matcher.find()) {
            final Integer referencedIssue = Integer.parseInt(matcher.group(1));
            issues.add(referencedIssue);
        }
        return issues;
    }

    private String getBreakingArea(Set<String> labels) {
        final Function<String, String> labelToBreakingArea = l -> {
            switch (l) {
                case "ml":
                    return "Machine Learning";

                case "Distributed/Discovery-Plugins":
                    return "Discovery";

                case "Distributed/Snapshot/Restore":
                    return "Snapshot and Restore";

                default:
                    final String[] split = l.substring(1).split("/");
                    return split[split.length - 1];
            }
        };

        return labels.stream()
            .filter(l -> l.startsWith(":"))
            .filter(l -> l.startsWith("Delivery") == false)
            .map(labelToBreakingArea)
            .collect(Collectors.joining(", "));
    }
}
