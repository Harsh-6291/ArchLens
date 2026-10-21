package com.archlens.backend.dto;

import java.util.List;

public record CreateEnvironmentRequest(
        String name,
        List<EnvironmentVariable> variables
) {}
