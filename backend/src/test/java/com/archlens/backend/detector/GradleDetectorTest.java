package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradleDetectorTest {

    private GradleDetector detector;

    @BeforeEach
    void setUp() {
        detector = new GradleDetector();
    }

    @Test
    void detectsGradleJavaService() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/gradle").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("order-service", node.name());
        assertEquals("SERVICE", node.type());
        assertEquals("Java (Gradle)", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("build.gradle", node.sourceFile());
    }

    @Test
    void returnsEmptyWhenNoGradleFileFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
