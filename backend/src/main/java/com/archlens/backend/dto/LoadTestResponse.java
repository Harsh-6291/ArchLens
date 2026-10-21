package com.archlens.backend.dto;

import com.archlens.backend.entity.LoadTest;
import java.time.Instant;
import java.util.UUID;

public record LoadTestResponse(
        UUID id,
        UUID apiDefinitionId,
        UUID environmentId,
        String name,
        String loadProfile,
        Integer virtualUsers,
        Integer durationSeconds,
        Integer rampUpSeconds,
        String status,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static LoadTestResponse from(LoadTest lt) {
        return new LoadTestResponse(
                lt.getId(),
                lt.getApiDefinition() != null ? lt.getApiDefinition().getId() : null,
                lt.getEnvironment() != null ? lt.getEnvironment().getId() : null,
                lt.getName(),
                lt.getLoadProfile(),
                lt.getVirtualUsers(),
                lt.getDurationSeconds(),
                lt.getRampUpSeconds(),
                lt.getStatus(),
                lt.getErrorMessage(),
                lt.getCreatedAt(),
                lt.getStartedAt(),
                lt.getCompletedAt()
        );
    }
}
