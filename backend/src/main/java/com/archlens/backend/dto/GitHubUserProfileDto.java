package com.archlens.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUserProfileDto(
        Long id,
        String login,
        String email,
        @JsonProperty("avatar_url") String avatarUrl
) {}
