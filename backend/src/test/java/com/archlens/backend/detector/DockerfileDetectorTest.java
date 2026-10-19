package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockerfileDetectorTest {

    private DockerfileDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DockerfileDetector();
    }

    @Test
    void detectsDockerfileContainer() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/docker").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("docker-container", node.name());
        assertEquals("CONTAINER", node.type());
        assertEquals("Docker", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("Dockerfile", node.sourceFile());
        assertEquals("openjdk:21-jre-slim", node.metadata().get("baseImage"));
    }

    @Test
    void returnsEmptyWhenNoDockerfileFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
