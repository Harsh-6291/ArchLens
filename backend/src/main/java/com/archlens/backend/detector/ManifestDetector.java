package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import java.nio.file.Path;
import java.util.List;

public interface ManifestDetector {
    /**
     * Name identifier for this detector.
     */
    String getName();

    /**
     * Checks whether the workspace contains manifests or files supported by this detector.
     */
    boolean canDetect(Path workspaceRoot);

    /**
     * Inspects the workspace and extracts detected architectural nodes.
     */
    List<DetectedNode> detect(Path workspaceRoot);
}
