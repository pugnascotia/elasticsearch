/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.WordUtils;
import org.elasticsearch.gradle.release.github.GitHubPullRequest;
import org.elasticsearch.gradle.release.github.GitHubPullRequestEvent;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

public class GenerateChangelogEntryTask extends DefaultTask {
    private static final String BREAKING = ">breaking";
    private static final String BREAKING_JAVA = ">breaking-java";
    private static final String DEPRECATION = ">deprecation";
    private static final String RELEASE_HIGHLIGHT = "release highlight";

    private static final Set<String> IGNORED_LABELS = Set.of(
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

    private static final Set<String> FILTERED_LABELS = Set.of(">new-field-mapper");

    private final Map<String, String> AREA_OVERRIDES = Map.of(
        "ml", "Machine Learning",
        "Beats", "Beats Plugin",
        "Docs", "Docs Infrastructure"
    );

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

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final GitHubPullRequestEvent pr = mapper.readValue(payloadBytes, GitHubPullRequestEvent.class);

        final Path changelogPath = getChangelogPath(pr.getNumber());

        ChangelogEntry entry;
        if (Files.exists(changelogPath)) {
            entry = ChangelogEntry.parse(changelogPath.toFile());

            updateChangelogEntry(pr.getPullRequest(), entry);
        } else {
            entry = createChangelogEntry(pr.getPullRequest());
        }

        Files.writeString(changelogPath, entry.toYaml());
    }

    /**
     * Creates a new {@link ChangelogEntry} instances from the supplied PR.
     */
    private ChangelogEntry createChangelogEntry(GitHubPullRequest pr) {
        ChangelogEntry entry = new ChangelogEntry();

        Set<String> labels = pr.getLabelValues();
        String summary = prepareIssueTitle(pr.getTitle());
        String area = getArea(labels);
        String body = wrap(pr.getBody());

        entry.setPr(pr.getNumber());
        entry.setSummary(summary);
        entry.setIssues(getReferencesIssues(pr));
        entry.setArea(area);
        entry.setType(getType(labels));
        entry.setVersions(pr.getVersions());

        if (labels.contains(RELEASE_HIGHLIGHT)) {
            ChangelogEntry.Highlight h = new ChangelogEntry.Highlight();
            h.setTitle(summary);
            h.setBody(body);
            entry.setHighlight(h);
        }

        if (labels.contains(BREAKING) || labels.contains(BREAKING_JAVA)) {
            ChangelogEntry.Breaking b = new ChangelogEntry.Breaking();
            b.setArea(area);
            b.setTitle(summary);
            b.setDetails(body);
            b.setImpact("Please describe the impact of this change to users");
            entry.setBreaking(b);
        }

        if (labels.contains(DEPRECATION)) {
            ChangelogEntry.Deprecation d = new ChangelogEntry.Deprecation();
            d.setArea(area);
            d.setTitle(summary);
            d.setBody(body);
            entry.setDeprecation(d);
        }

        return entry;
    }

    /**
     * Updates a {@link ChangelogEntry} using the supplied PR. Only some fields are updated, since
     * an author may have updated the file.
     */
    private void updateChangelogEntry(GitHubPullRequest pr, ChangelogEntry entry) {
        Set<String> labels = pr.getLabelValues();
        String area = getArea(labels);
        String type = getType(labels);
        Set<String> versions = pr.getVersions();
        Set<Integer> referencesIssues = getReferencesIssues(pr);

        // Note that we don't update the area because it is legitimate to label a PR for more than one
        // team in order to get a broader review. In that case, the PR author needs to decide which team
        // is most relevant for the changelog, and we don't want to overwrite it.

        if (type.equals(entry.getType()) == false) {
            entry.setType(type);
        }

        if (versions.equals(entry.getVersions()) == false) {
            entry.setVersions(versions);
        }

        if (referencesIssues.equals(entry.getIssues()) == false) {
            entry.setIssues(referencesIssues);
        }

        String summary = prepareIssueTitle(pr.getTitle());
        String body = wrap(pr.getBody());

        if (labels.contains(RELEASE_HIGHLIGHT) && entry.getHighlight() == null) {
            ChangelogEntry.Highlight h = new ChangelogEntry.Highlight();
            h.setTitle(summary);
            h.setBody(body);
            entry.setHighlight(h);
        }

        if ((labels.contains(BREAKING) || labels.contains(BREAKING_JAVA)) && entry.getBreaking() == null) {
            ChangelogEntry.Breaking b = new ChangelogEntry.Breaking();
            b.setArea(area);
            b.setTitle(summary);
            b.setDetails(body);
            b.setImpact("Please describe the impact of this change to users");
            // `notable` here refers to whether the corresponding generated asciidoc is wrapped in a "notable"
            // section, and is not related to whether this PR is a release highlight. However, we can use
            // the presence of that label as a guide.
            b.setNotable(labels.contains(RELEASE_HIGHLIGHT));
            entry.setBreaking(b);
        }

        if (labels.contains(DEPRECATION) && entry.getDeprecation() == null) {
            ChangelogEntry.Deprecation d = new ChangelogEntry.Deprecation();
            d.setArea(area);
            d.setTitle(summary);
            d.setBody(body);
            entry.setDeprecation(d);
        }
    }

    private Path getChangelogPath(int prNumber) {
        return Path.of("docs/changelog/" + prNumber + ".yaml");
    }

    /**
     * Transforms a PR title into a suitable title for a changelog entry. This mostly involves
     * removing text.
     */
    @VisibleForTesting
    String prepareIssueTitle(String title) {
        // Remove prefixes from the title.
        String areasRegex = "(?i)(?:ml|beats|docs|transform|[es]?ql)";
        title = title.replaceFirst("^\\[" + areasRegex + "]\\s+", "");
        title = title.replaceFirst("^" + areasRegex + ":\\s+", "");

        // Remove any PR number prefix
        title = title.replaceFirst("^#\\d+:?\\s+", "");

        // Remove any PR or issue number suffixes
        title = title.replaceFirst("(?: \\(#\\d+\\))+", "");

        // Capitalise
        title = title.substring(0, 1).toUpperCase() + title.substring(1);
        title = title.replaceFirst("\\.$", "");

        if (title.endsWith(".")) {
            title = title.substring(0, title.length() - 1);
        }

        // Attempt to wrap camel-case words or snake-case words in backticks
        Function<String, String> quoteWords = token -> {
            boolean quote = false;
            if (token.matches("[A-Z]?[a-z]+[A-Z][a-z]+.*")) {
                quote = true;
            } else if (token.matches("^[a-z]*(?:[._][a-z]+)+$")) {
                quote = true;
            }

            return quote ? '`' + token + '`' : token;
        };
        title = Arrays.stream(title.split("\\s+")).map(quoteWords).collect(joining(" "));

        return title.trim();
    }

    /**
     * Identifies issues or PRs that are referenced in the body (description) of a PR.
     */
    private Set<Integer> getReferencesIssues(GitHubPullRequest pr) {
        String body = pr.getBody();
        if (body == null || body.isEmpty()) {
            return Set.of();
        }

        Pattern referencedIssues = Pattern.compile("(?:#|" + pr.getRepository() + "/issues/)(\\d+)");
        Matcher matcher = referencedIssues.matcher(body);

        return matcher.results().map(matchResult -> Integer.parseInt(matchResult.group(1))).collect(toSet());
    }

    /**
     * Finds the (team) area for a PR given a set of labels. Ideally this would only return a single
     * value, but sometimes PRs are labelled for the attention of multiple teams.
     *
     * @return all area labels, concatenated together by ", "
     */
    @VisibleForTesting
    String getArea(Set<String> labels) {
        List<String> areas = labels.stream().filter(l -> l.startsWith(":")).map(l -> {
            String area = l.replaceFirst("^:(?:[^/]+/)?", "");
            return AREA_OVERRIDES.getOrDefault(area, area);
        }).collect(Collectors.toList());

        if (areas.size() == 2 && areas.contains("SQL") && areas.contains("EQL")) {
            return "Query Languages";
        }

        return String.join(", ", areas);
    }

    /**
     * Finds the change type for a PR given a set of labels.
     *
     * @return all change type labels, concatenated together by ", "
     */
    private String getType(Set<String> labels) {
        return labels.stream()
            .filter(l -> l.startsWith(">") && FILTERED_LABELS.contains(l) == false)
            .map(l -> l.substring(1))
            .collect(joining(", "));
    }

    /**
     * Wraps the supplied text at 72 columns.
     *
     * @return a wrapped copy of the input.
     */
    private String wrap(String input) {
        return WordUtils.wrap(input, 72);
    }

    private boolean shouldHandle(GitHubPullRequest pr) {
        return pr.isDraft() == false
            && pr.isLocked() == false
            && pr.isClosed() == false
            && Collections.disjoint(pr.getLabelValues(), IGNORED_LABELS)
            && pr.hasLabelIssues();
    }

}
