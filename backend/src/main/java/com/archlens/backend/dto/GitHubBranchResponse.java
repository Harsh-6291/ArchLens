package com.archlens.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubBranchResponse(
        String name,
        CommitRef commit
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitRef(String sha) {}
}
