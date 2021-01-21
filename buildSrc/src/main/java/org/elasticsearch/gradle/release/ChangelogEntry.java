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

import org.elasticsearch.gradle.Version;
import org.gradle.api.GradleException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ChangelogEntry {
    private int pr;
    private List<Integer> issues;
    private String area;
    private String type;
    private String summary;
    private Highlight highlight;
    private Breaking breaking;
    private List<String> versions;

    public int getPr() {
        return pr;
    }

    public void setPr(int pr) {
        this.pr = pr;
    }

    public List<Integer> getIssues() {
        return issues;
    }

    public void setIssues(List<Integer> issues) {
        this.issues = issues;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Highlight getHighlight() {
        return highlight;
    }

    public void setHighlight(Highlight highlight) {
        this.highlight = highlight;
    }

    public Breaking getBreaking() {
        return breaking;
    }

    public void setBreaking(Breaking breaking) {
        this.breaking = breaking;
    }

    public List<String> getVersions() {
        return versions;
    }

    public void setVersions(List<String> versions) {
        this.versions = versions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChangelogEntry that = (ChangelogEntry) o;
        return pr == that.pr
            && Objects.equals(issues, that.issues)
            && Objects.equals(area, that.area)
            && Objects.equals(type, that.type)
            && Objects.equals(summary, that.summary)
            && Objects.equals(highlight, that.highlight)
            && Objects.equals(breaking, that.breaking)
            && Objects.equals(versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pr, issues, area, type, summary, highlight, breaking, versions);
    }

    @Override
    public String toString() {
        return String.format(
            Locale.ROOT,
            "ChangelogEntry{pr=%d, issues=%s, area='%s', type='%s', summary='%s', highlight=%s, breaking=%s, versions=%s}",
            pr,
            issues,
            area,
            type,
            summary,
            highlight,
            breaking,
            versions
        );
    }

    // Use a TreeSet so that, if printed, the areas are printed in order
    private final Set<String> KNOWN_TYPES = new TreeSet<>(
        Set.of("breaking", "breaking-java", "deprecation", "feature", "enhancement", "bug", "regression", "upgrade")
    );

    // Use a TreeSet so that, if printed, the areas are printed in order
    private final Set<String> KNOWN_AREAS = new TreeSet<>(
        Set.of(
            "Aggregations",
            "Allocation",
            "Authorization",
            "Cluster Coordination",
            "Distributed",
            "EQL",
            "Engine",
            "Engine changes",
            "Features/Features",
            "Features/ILM+SLM",
            "Features/Indices APIs",
            "Features/Ingest",
            "Features/Monitoring",
            "Geo",
            "Infra/Core",
            "Infra/Logging",
            "Infra/Plugins",
            "Infra/Scripting",
            "Infra/Settings",
            "Machine Learning",
            "Packaging",
            "Query Languages",
            "Ranking",
            "Rollup",
            "Search",
            "Security",
            "SQL",
            "Task Management"
        )
    );

    public void validate() {
        if (this.pr < 1) {
            throw new IllegalArgumentException("Invalid PR number [" + this.pr + "]");
        }

        required(this.summary, "summary must be supplied");
        required(this.type, "type must be supplied");
        required(this.area, "area must be supplied");

        if (KNOWN_TYPES.contains(this.type) == false) {
            throw new IllegalArgumentException("Unknown type [" + this.type + "], expected one of: " + KNOWN_TYPES);
        }

        if (KNOWN_AREAS.contains(this.area) == false) {
            throw new IllegalArgumentException("Unknown area [" + this.area + "], expected one of: " + KNOWN_AREAS);
        }

        if (this.versions == null || this.versions.isEmpty()) {
            throw new IllegalArgumentException("At least one version must be supplied");
        }

        for (String version : this.versions) {
            // Allow exceptions to bubble up
            Version.fromString(version, Version.Mode.RELAXED);
        }

        if (this.highlight != null) {
            this.highlight.validate();
        }

        if (this.breaking != null) {
            this.breaking.validate();
        }
    }

    public static class Highlight {
        private boolean notable;
        private String title;
        private String body;

        public boolean isNotable() {
            return notable;
        }

        public void setNotable(boolean notable) {
            this.notable = notable;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public void validate() {
            required(this.title, "Highlight must have a title supplied");
            required(this.body, "Highlight must have a body supplied");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Highlight highlight = (Highlight) o;
            return Objects.equals(notable, highlight.notable)
                && Objects.equals(title, highlight.title)
                && Objects.equals(body, highlight.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notable, title, body);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Highlight{notable=%s, title='%s', body='%s'}", notable, title, body);
        }
    }

    public static class Breaking {
        private String area;
        private String title;
        private String body;
        private boolean isNotable;
        private String anchor;

        // Use a TreeSet so that, if printed, the areas are printed in order
        private static final Set<String> KNOWN_BREAKING_AREAS = new TreeSet<>(
            Set.of(
                "API",
                "Aggregation",
                "Allocation",
                "Authentication",
                "CCR",
                "Cluster",
                "Discovery",
                "Engine",
                "HTTP",
                "Highlighters",
                "Indices",
                "Java",
                "License Information",
                "Logging",
                "Machine learning",
                "Mappings",
                "Networking",
                "Plugins",
                "Script cache",
                "Search Changes",
                "Search",
                "Security",
                "Settings",
                "Snapshot and Restore",
                "Transform",
                "Transport"
            )
        );

        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public boolean isNotable() {
            return isNotable;
        }

        public void setNotable(boolean notable) {
            isNotable = notable;
        }

        public String getAnchor() {
            return anchor;
        }

        public void setAnchor(String anchor) {
            this.anchor = anchor;
        }

        public void validate() {
            required(this.area, "Breaking info must have an area supplied");
            required(this.title, "Breaking info must have a title supplied");
            required(this.body, "Breaking info must have a body supplied");

            if (KNOWN_BREAKING_AREAS.contains(this.area) == false) {
                throw new IllegalArgumentException("Unknown breaking area [" + this.area + "], expected one of: " + KNOWN_BREAKING_AREAS);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Breaking breaking = (Breaking) o;
            return Objects.equals(area, breaking.area)
                && Objects.equals(title, breaking.title)
                && Objects.equals(body, breaking.body)
                && Objects.equals(isNotable, breaking.isNotable)
                && Objects.equals(anchor, breaking.anchor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(area, title, body, isNotable, anchor);
        }

        @Override
        public String toString() {
            return String.format(
                Locale.ROOT,
                "Breaking{area='%s', title='%s', body='%s', isNotable=%s, anchor='%s'}",
                area,
                title,
                body,
                isNotable,
                anchor
            );
        }
    }

    private static void required(String value, String error) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(error);
        }
    }

    public static ChangelogEntry parseChangelog(File file) {
        final ChangelogEntry changelogEntry;

        try {
            Yaml yaml = new Yaml();
            final String source = new String(Files.readAllBytes(file.toPath()));
            changelogEntry = yaml.loadAs(source, ChangelogEntry.class);
        } catch (Exception e) {
            throw new GradleException("Failed to load changelog [" + file + "]: " + e.getMessage(), e);
        }

        try {
            changelogEntry.validate();
        } catch (IllegalArgumentException e) {
            throw new GradleException("Validation failed for changelog [" + file + "]: " + e.getMessage(), e);
        }

        return changelogEntry;
    }
}
