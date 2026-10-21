package com.archlens.backend.dto;

import java.util.List;
import java.util.UUID;

public record ArchitectureGraphDto(
        UUID graphId,
        UUID analysisJobId,
        UUID repositoryId,
        String commitSha,
        List<GraphNodeDto> nodes,
        List<GraphEdgeDto> edges
) {
    public ArchitectureGraphDto {
        if (nodes == null) nodes = List.of();
        if (edges == null) edges = List.of();
    }
}
