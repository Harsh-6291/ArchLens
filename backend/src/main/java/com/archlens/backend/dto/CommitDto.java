package com.archlens.backend.dto;

public record CommitDto(
        String sha,
        String message,
        String authoredAt
) {}
