package com.archlens.backend.dto;

import java.util.UUID;

public record CreateAnalysisJobResponseDto(
        UUID jobId,
        String status
) {}
