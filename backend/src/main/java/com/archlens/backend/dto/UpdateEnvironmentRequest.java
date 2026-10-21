package com.archlens.backend.dto;

import java.util.List;

public record UpdateEnvironmentRequest(
        String name,
        List<EnvironmentVariable> variables
) {}
