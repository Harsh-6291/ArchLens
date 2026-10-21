package com.archlens.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiDefinitionDto(
        UUID id,
        UUID graphId,
        UUID graphNodeId,
        String method,
        String path,
        String handlerClass,
        String handlerMethod,
        String sourceFile,
        Integer lineNumber,
        Boolean discovered,
        Object metadata,
        Instant createdAt
) {}
