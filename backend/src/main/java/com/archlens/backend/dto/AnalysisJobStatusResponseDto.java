package com.archlens.backend.dto;

import java.util.UUID;

public record AnalysisJobStatusResponseDto(
        UUID jobId,
        String status,
        Progress progress,
        UUID graphId,
        String errorMessage
) {
    public record Progress(
            String stage,
            Integer percent
    ) {}
}
