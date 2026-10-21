package com.archlens.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestPayload(
        String action,
        int number,
        @JsonProperty("pull_request") PullRequestDetails pullRequest,
        GitHubRepoDetails repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestDetails(
            long id,
            int number,
            String title,
            String state,
            HeadCommit head,
            BaseCommit base
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadCommit(
            String sha,
            String ref
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaseCommit(
            String sha,
            String ref
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubRepoDetails(
            long id,
            String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("default_branch") String defaultBranch
    ) {}
}
