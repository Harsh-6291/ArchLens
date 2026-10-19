package com.archlens.backend.dto;

public record AuthCallbackResponseDto(
        UserDto user,
        String returnRepoUrl
) {}
