package com.archlens.backend.dto;

import java.util.UUID;

public record GraphNodeDto(
        UUID id,
        String nodeKey,
        String name,
        String type,
        String technology,
        Double confidence,
        String sourceFile,
        Object metadata
) {}
