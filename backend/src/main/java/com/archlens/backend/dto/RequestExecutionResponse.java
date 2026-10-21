package com.archlens.backend.dto;

import com.archlens.backend.entity.RequestExecution;

import java.time.Instant;
import java.util.UUID;

public record RequestExecutionResponse(
        UUID id,
        UUID apiDefinitionId,
        UUID savedRequestId,
        Integer statusCode,
        Integer latencyMs,
        String responseHeaders,
        String responseBodyExcerpt,
        String error,
        Instant executedAt
) {
    public static RequestExecutionResponse from(RequestExecution execution) {
        return new RequestExecutionResponse(
                execution.getId(),
                execution.getApiDefinition() != null ? execution.getApiDefinition().getId() : null,
                execution.getSavedRequestId(),
                execution.getStatusCode(),
                execution.getLatencyMs(),
                execution.getResponseHeaders(),
                execution.getResponseBodyExcerpt(),
                execution.getError(),
                execution.getExecutedAt()
        );
    }
}
