package com.archlens.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoDto(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("private") boolean isPrivate,
        @JsonProperty("html_url") String htmlUrl,
        String description
) {}
