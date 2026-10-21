package com.archlens.backend.dto;

import com.archlens.backend.entity.SavedRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API response shape for a saved request.
 */
public record SavedRequestResponse(
        UUID id,
        UUID apiDefinitionId,
        String name,
        String description,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        Map<String, String> headers,
        Object body,
        UUID environmentId,
        Instant createdAt,
        Instant updatedAt
) {
    public static SavedRequestResponse from(SavedRequest sr) {
        return new SavedRequestResponse(
                sr.getId(),
                sr.getApiDefinition().getId(),
                sr.getName(),
                sr.getDescription(),
                sr.getPathParams(),
                sr.getQueryParams(),
                sr.getHeaders(),
                sr.getBody(),
                sr.getEnvironment() != null ? sr.getEnvironment().getId() : null,
                sr.getCreatedAt(),
                sr.getUpdatedAt()
        );
    }
}
