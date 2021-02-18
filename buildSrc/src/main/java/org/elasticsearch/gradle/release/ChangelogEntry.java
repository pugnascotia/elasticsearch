/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * This class models the contents of a changelog YAML file. It contains no validation of its own,
 * because we check it against a JSON Schema document.
 *
 * @see <code>buildSrc/src/main/resources/changelog-schema.json</code>
 * @see <a href="https://json-schema.org/understanding-json-schema/">Understanding JSON Schema</a>
 */
public class ChangelogEntry {
    private int pr;
    private String summary;
    private String area;
    private String type;
    private List<Integer> issues;
    private List<String> versions;
    private Highlight highlight;
    private Breaking breaking;
    private Deprecation deprecation;

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

    public Deprecation getDeprecation() {
        return deprecation;
    }

    public void setDeprecation(Deprecation deprecation) {
        this.deprecation = deprecation;
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
            "ChangelogEntry{pr=%d, issues=%s, area='%s', type='%s', summary='%s', highlight=%s, breaking=%s, deprecation=%s versions=%s}",
            pr,
            issues,
            area,
            type,
            summary,
            highlight,
            breaking,
            deprecation,
            versions
        );
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
        private String details;
        private String impact;
        private boolean isNotable;
        private String anchor;

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

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Breaking breaking = (Breaking) o;
            return isNotable == breaking.isNotable
                && Objects.equals(area, breaking.area)
                && Objects.equals(title, breaking.title)
                && Objects.equals(details, breaking.details)
                && Objects.equals(impact, breaking.impact)
                && Objects.equals(anchor, breaking.anchor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(area, title, details, impact, isNotable, anchor);
        }

        @Override
        public String toString() {
            return String.format(
                "Breaking{area='%s', title='%s', details='%s', impact='%s', isNotable=%s, anchor='%s'}",
                area,
                title,
                details,
                impact,
                isNotable,
                anchor
            );
        }
    }

    public static class Deprecation {
        private String area;
        private String title;
        private String body;
        private String anchor;

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

        public String getAnchor() {
            return anchor;
        }

        public void setAnchor(String anchor) {
            this.anchor = anchor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Deprecation that = (Deprecation) o;
            return Objects.equals(area, that.area)
                && Objects.equals(title, that.title)
                && Objects.equals(body, that.body)
                && Objects.equals(anchor, that.anchor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(area, title, body, anchor);
        }

        @Override
        public String toString() {
            return String.format("Deprecation{area='%s', title='%s', body='%s', anchor='%s'}", area, title, body, anchor);
        }
    }
}
