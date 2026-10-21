package com.archlens.backend.dto;

import java.util.Map;
import java.util.UUID;

public record ExecuteApiRequest(
        Map<String, String> headers,
        Map<String, String> queryParams,
        Map<String, String> pathParams,
        Object body,
        UUID environmentId
) {}
