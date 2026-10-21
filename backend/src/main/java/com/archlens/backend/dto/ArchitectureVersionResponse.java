package com.archlens.backend.dto;

import com.archlens.backend.entity.ArchitectureVersion;
import java.time.Instant;
import java.util.UUID;

public record ArchitectureVersionResponse(
        UUID id,
        UUID graphId,
        String versionName,
        String description,
        Instant createdAt
) {
    public static ArchitectureVersionResponse from(ArchitectureVersion v) {
        return new ArchitectureVersionResponse(
                v.getId(),
                v.getGraph().getId(),
                v.getVersionName(),
                v.getDescription(),
                v.getCreatedAt()
        );
    }
}
