package com.archlens.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EnvironmentResponse(
        UUID id,
        UUID userId,
        String name,
        List<EnvironmentVariable> variables,
        Instant createdAt,
        Instant updatedAt
) {}
