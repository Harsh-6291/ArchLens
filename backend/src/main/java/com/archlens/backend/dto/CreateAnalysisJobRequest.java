package com.archlens.backend.dto;

import java.util.UUID;

public record CreateAnalysisJobRequest(
        UUID repositoryId,
        String branch,
        String commitSha
) {}
