package com.archlens.backend.dto;

import java.util.UUID;

public record CreateApiDefinitionRequest(
        UUID graphNodeId,
        String method,
        String path,
        String authType,
        Object metadata
) {}
