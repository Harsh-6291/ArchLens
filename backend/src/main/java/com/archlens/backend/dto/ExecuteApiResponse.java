package com.archlens.backend.dto;

import java.util.Map;
import java.util.UUID;

public record ExecuteApiResponse(
        UUID executionId,
        Integer statusCode,
        Integer latencyMs,
        Map<String, String> responseHeaders,
        Object responseBody,
        String error
) {}
