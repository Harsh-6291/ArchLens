package com.archlens.backend.dto;

import java.util.List;
import java.util.UUID;

public record GraphDiffResponse(
        UUID sourceGraphId,
        UUID versionId,
        String versionName,
        List<GraphNodeDto> addedNodes,
        List<GraphNodeDto> removedNodes,
        List<ModifiedNodeDiff> modifiedNodes,
        List<GraphEdgeDto> addedEdges,
        List<GraphEdgeDto> removedEdges
) {
    public record ModifiedNodeDiff(
            String nodeKey,
            String oldName,
            String newName,
            String oldType,
            String newType,
            String oldTechnology,
            String newTechnology
    ) {}
}
