package com.archlens.backend.dto;

import java.util.UUID;

public record CreateLoadTestRequest(
        String name,
        UUID environmentId,
        String loadProfile,
        Integer virtualUsers,
        Integer durationSeconds,
        Integer rampUpSeconds
) {}
