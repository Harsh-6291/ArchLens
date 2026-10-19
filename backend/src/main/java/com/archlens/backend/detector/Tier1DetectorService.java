package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import com.archlens.backend.detector.model.DetectionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

@Service
public class Tier1DetectorService {

    private static final Logger log = LoggerFactory.getLogger(Tier1DetectorService.class);

    private final List<ManifestDetector> detectors;
    private final ObjectMapper objectMapper;

    public Tier1DetectorService(List<ManifestDetector> detectors, ObjectMapper objectMapper) {
        this.detectors = detectors != null ? detectors : List.of();
        this.objectMapper = objectMapper;
    }

    /**
     * Executes all Tier 1 generic manifest detectors on the given workspace path.
     */
    public DetectionResult detect(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return new DetectionResult(List.of());
        }

        Map<String, DetectedNode> nodesById = new LinkedHashMap<>();

        for (ManifestDetector detector : detectors) {
            try {
                if (detector.canDetect(workspaceRoot)) {
                    log.info("Running Tier 1 detector '{}' on {}", detector.getName(), workspaceRoot);
                    List<DetectedNode> detectedNodes = detector.detect(workspaceRoot);
                    if (detectedNodes != null) {
                        for (DetectedNode node : detectedNodes) {
                            String key = node.id();
                            if (nodesById.containsKey(key)) {
                                DetectedNode existing = nodesById.get(key);
                                if (node.confidence() > existing.confidence()) {
                                    log.info("Upgrading node '{}' with higher confidence detection ({}) from '{}'",
                                            key, node.confidence(), detector.getName());
                                    nodesById.put(key, node);
                                }
                            } else {
                                nodesById.put(key, node);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error executing detector '{}' on {}: {}", detector.getName(), workspaceRoot, e.getMessage(), e);
            }
        }

        List<DetectedNode> allNodes = new ArrayList<>(nodesById.values());
        log.info("Tier 1 detection finished for {}: found {} nodes", workspaceRoot, allNodes.size());
        return new DetectionResult(allNodes);
    }

    /**
     * Serializes a DetectionResult to a JSON string.
     */
    public String toJson(DetectionResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DetectionResult to JSON: {}", e.getMessage(), e);
            return "{\"nodes\":[]}";
        }
    }
}
