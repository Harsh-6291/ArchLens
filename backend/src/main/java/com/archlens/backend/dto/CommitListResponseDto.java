package com.archlens.backend.dto;

import java.util.List;

public record CommitListResponseDto(
        List<CommitDto> commits
) {}
