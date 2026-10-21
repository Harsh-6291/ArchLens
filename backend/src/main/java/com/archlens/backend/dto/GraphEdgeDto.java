package com.archlens.backend.dto;

import java.util.UUID;

public record GraphEdgeDto(
        UUID id,
        UUID source,
        UUID target,
        String type,
        Double confidence,
        Object metadata
) {}
