package com.archlens.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitResponse(
        String sha,
        CommitDetails commit
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitDetails(
            String message,
            AuthorDetails author
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorDetails(
            String name,
            String date
    ) {}
}
