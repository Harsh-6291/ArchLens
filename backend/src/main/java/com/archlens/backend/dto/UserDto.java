package com.archlens.backend.dto;

import java.util.UUID;

public record UserDto(
        UUID id,
        String githubUsername,
        String avatarUrl
) {}
