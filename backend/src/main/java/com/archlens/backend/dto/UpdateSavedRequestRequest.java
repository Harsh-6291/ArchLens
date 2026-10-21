package com.archlens.backend.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Request body for updating an existing saved request.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateSavedRequestRequest(
        String name,
        String description,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        Map<String, String> headers,
        Object body,
        UUID environmentId
) {}
