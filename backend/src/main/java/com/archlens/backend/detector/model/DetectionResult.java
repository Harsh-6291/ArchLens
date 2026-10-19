package com.archlens.backend.detector.model;

import java.util.List;

public record DetectionResult(
    List<DetectedNode> nodes
) {
    public DetectionResult {
        if (nodes == null) {
            nodes = List.of();
        }
    }
}
