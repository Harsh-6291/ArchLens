package com.archlens.backend.dto;

import java.util.List;

public record ApiDefinitionsListResponse(
        List<ApiDefinitionDto> apiDefinitions
) {}
