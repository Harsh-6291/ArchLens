package com.archlens.backend.dto;

import java.util.UUID;

public record RepoResponseDto(
        UUID id,
        String fullName,
        String defaultBranch,
        String accessMethod,
        boolean authorizedForTesting
) {}
