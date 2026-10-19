package com.archlens.backend.dto;

import java.util.List;

public record BranchListResponseDto(
        List<BranchDto> branches
) {}
