package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import com.archlens.backend.detector.model.DetectionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Tier1DetectorServiceTest {

    private Tier1DetectorService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        List<ManifestDetector> detectors = List.of(
                new PackageJsonDetector(objectMapper),
                new MavenDetector(),
                new GradleDetector(),
                new PythonRequirementsDetector(),
                new GoModDetector(),
                new DockerfileDetector(),
                new DockerComposeDetector()
        );
        service = new Tier1DetectorService(detectors, objectMapper);
    }

    @Test
    void aggregatesAllNodesAcrossManifestTypes() {
        Path fixturesRoot = Paths.get("src/test/resources/fixtures").toAbsolutePath();
        DetectionResult result = service.detect(fixturesRoot);

        assertNotNull(result);
        assertFalse(result.nodes().isEmpty());

        // We expect nodes for: nodejs, react, maven, gradle, python, go, docker, and docker-compose (4)
        assertTrue(result.nodes().size() >= 8);

        // Verify JSON serialization
        String json = service.toJson(result);
        assertNotNull(json);
        assertTrue(json.contains("ecommerce-api"));
        assertTrue(json.contains("inventory-service"));
        assertTrue(json.contains("order-service"));
        assertTrue(json.contains("PostgreSQL"));
        assertTrue(json.contains("Redis"));
    }

    @Test
    void handlesNullOrEmptyWorkspaceGracefully() {
        DetectionResult nullResult = service.detect(null);
        assertNotNull(nullResult);
        assertTrue(nullResult.nodes().isEmpty());

        String json = service.toJson(nullResult);
        assertEquals("{\"nodes\":[]}", json);
    }
}
