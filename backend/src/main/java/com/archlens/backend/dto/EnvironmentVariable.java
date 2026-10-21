package com.archlens.backend.dto;

public record EnvironmentVariable(
        String key,
        String value,
        boolean secret
) {}
