package com.archlens.backend.detector.model;

import java.util.Map;

public record DetectedNode(
    String id,
    String name,
    String type,
    String technology,
    double confidence,
    String sourceFile,
    Map<String, Object> metadata
) {
    public DetectedNode {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
