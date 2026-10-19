package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PythonRequirementsDetectorTest {

    private PythonRequirementsDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PythonRequirementsDetector();
    }

    @Test
    void detectsPythonService() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/python").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("python", node.name());
        assertEquals("SERVICE", node.type());
        assertEquals("Python", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("requirements.txt", node.sourceFile());

        @SuppressWarnings("unchecked")
        List<String> packages = (List<String>) node.metadata().get("topPackages");
        assertNotNull(packages);
        assertTrue(packages.contains("fastapi"));
        assertTrue(packages.contains("sqlalchemy"));
    }

    @Test
    void returnsEmptyWhenNoPythonManifestFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
