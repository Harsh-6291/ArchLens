package com.archlens.backend.dto;

public record CreateVersionRequest(
        String versionName,
        String description
) {}
