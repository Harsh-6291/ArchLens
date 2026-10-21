package com.archlens.backend.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Request body for creating a new saved request.
 */
public record CreateSavedRequestRequest(
        String name,
        String description,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        Map<String, String> headers,
        Object body,
        UUID environmentId
) {}
