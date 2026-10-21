package com.archlens.backend.dto;

import java.util.UUID;

/**
 * M1.13 — Request body for manually adding an edge to a graph.
 */
public record CreateEdgeRequest(
        UUID sourceNodeId,
        UUID targetNodeId,
        String edgeType
) {}
