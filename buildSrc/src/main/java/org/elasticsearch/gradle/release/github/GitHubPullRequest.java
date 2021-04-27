/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release.github;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class GitHubPullRequest {
    private static final Pattern PATTERN = Pattern.compile("^v \\d+ \\. \\d+ \\. \\d+ (?: -(?: alpha|beta|rc )\\d+ )?$",
        Pattern.COMMENTS | Pattern.CASE_INSENSITIVE);

    private int number;
    private String title;
    private List<Label> labels;
    private String body;
    private Head head;
    private boolean locked;
    private String state;
    private boolean draft;

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Label> getLabels() {
        return labels;
    }

    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Head getHead() {
        return head;
    }

    public void setHead(Head head) {
        this.head = head;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public boolean isClosed() {
        return this.state.equals("closed");
    }

    public String getRepository() {
        return this.head.repo.fullName;
    }

    public Set<String> getLabelValues() {
        return this.labels.stream().map(l -> l.name).collect(Collectors.toSet());
    }

    public Set<String> getVersions() {
        return this.labels.stream().map(l -> l.name).filter(l -> PATTERN.matcher(l).find()).collect(Collectors.toSet());
    }

    public static class Label {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Label{" + "name='" + name + '\'' + '}';
        }
    }

    public static class Repo {
        private String fullName;

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        @Override
        public String toString() {
            return "Repo{" + "fullName='" + fullName + '\'' + '}';
        }
    }

    public static class Head {
        private Repo repo;

        public Repo getRepo() {
            return repo;
        }

        public void setRepo(Repo repo) {
            this.repo = repo;
        }

        @Override
        public String toString() {
            return "Head{" + "repo=" + repo + '}';
        }
    }

    public boolean hasLabelIssues() {
        return labels.stream().noneMatch(l -> PATTERN.matcher(l.name).find())
            && labels.stream().noneMatch(l -> l.name.startsWith(":"))
            && labels.stream().noneMatch(l -> l.name.startsWith(">"));
    }

    @Override
    public String toString() {
        return "GitHubPullRequest{"
               + "number="
               + number
               + ", title='"
               + title
               + '\''
               + ", labels="
               + labels
               + ", body='"
               + body
               + '\''
               + ", head="
               + head
               + ", locked="
               + locked
               + ", state='"
               + state
               + '\''
               + ", draft="
               + draft
               + '}';
    }
}
