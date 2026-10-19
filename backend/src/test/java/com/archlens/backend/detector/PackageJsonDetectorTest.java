package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackageJsonDetectorTest {

    private PackageJsonDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PackageJsonDetector(new ObjectMapper());
    }

    @Test
    void detectsNodeJsBackendService() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("ecommerce-api", node.name());
        assertEquals("SERVICE", node.type());
        assertEquals("TypeScript / Node.js", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("package.json", node.sourceFile());
    }

    @Test
    void detectsReactFrontendWebApplication() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/react").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("ecommerce-frontend", node.name());
        assertEquals("WEB_APPLICATION", node.type());
        assertEquals("TypeScript / Node.js", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("package.json", node.sourceFile());
    }

    @Test
    void returnsEmptyWhenNoManifestFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/docker").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
