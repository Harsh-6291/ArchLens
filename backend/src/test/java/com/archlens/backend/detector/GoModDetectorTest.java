package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoModDetectorTest {

    private GoModDetector detector;

    @BeforeEach
    void setUp() {
        detector = new GoModDetector();
    }

    @Test
    void detectsGoService() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/go").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("auth-service", node.name());
        assertEquals("SERVICE", node.type());
        assertEquals("Go", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("go.mod", node.sourceFile());
        assertEquals("github.com/example/auth-service", node.metadata().get("module"));
        assertEquals("1.22.1", node.metadata().get("goVersion"));
    }

    @Test
    void returnsEmptyWhenNoGoModFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
