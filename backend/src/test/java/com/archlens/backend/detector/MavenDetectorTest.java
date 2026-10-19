package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenDetectorTest {

    private MavenDetector detector;

    @BeforeEach
    void setUp() {
        detector = new MavenDetector();
    }

    @Test
    void detectsMavenJavaService() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/maven").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(1, nodes.size());

        DetectedNode node = nodes.get(0);
        assertEquals("inventory-service", node.name());
        assertEquals("SERVICE", node.type());
        assertEquals("Java (Maven)", node.technology());
        assertEquals(0.40, node.confidence());
        assertEquals("pom.xml", node.sourceFile());
        assertEquals("com.example.inventory", node.metadata().get("groupId"));
        assertEquals("inventory-service", node.metadata().get("artifactId"));
    }

    @Test
    void returnsEmptyWhenNoPomFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
