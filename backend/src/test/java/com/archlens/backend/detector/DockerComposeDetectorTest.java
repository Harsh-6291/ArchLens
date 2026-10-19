package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DockerComposeDetectorTest {

    private DockerComposeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DockerComposeDetector();
    }

    @Test
    void detectsComponentsFromDockerComposeWithoutLeakingSecrets() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/docker-compose").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(4, nodes.size());

        Map<String, DetectedNode> byName = nodes.stream()
                .collect(Collectors.toMap(DetectedNode::name, n -> n));

        // 1. PostgreSQL Database
        DetectedNode postgresNode = byName.get("postgres");
        assertNotNull(postgresNode);
        assertEquals("SQL_DATABASE", postgresNode.type());
        assertEquals("PostgreSQL", postgresNode.technology());
        assertEquals(0.50, postgresNode.confidence());
        assertEquals("docker-compose.yml", postgresNode.sourceFile());

        // 2. Redis Cache
        DetectedNode cacheNode = byName.get("cache");
        assertNotNull(cacheNode);
        assertEquals("CACHE", cacheNode.type());
        assertEquals("Redis", cacheNode.technology());
        assertEquals(0.50, cacheNode.confidence());

        // 3. RabbitMQ Message Broker
        DetectedNode mqNode = byName.get("message-queue");
        assertNotNull(mqNode);
        assertEquals("MESSAGE_BROKER", mqNode.type());
        assertEquals("RabbitMQ", mqNode.technology());
        assertEquals(0.50, mqNode.confidence());

        // 4. Custom web service
        DetectedNode webNode = byName.get("web-api");
        assertNotNull(webNode);
        assertEquals("SERVICE", webNode.type());
        assertEquals("Custom Container Service", webNode.technology());
        assertEquals(0.45, webNode.confidence());

        // CRITICAL: Ensure no secrets/passwords leaked into any metadata
        for (DetectedNode node : nodes) {
            String nodeRepr = node.toString();
            assertFalse(nodeRepr.contains("super_secret_password"),
                    "Node representation leaked sensitive password for " + node.name());
            assertFalse(nodeRepr.contains("secret_redis_pass_123"),
                    "Node representation leaked sensitive password for " + node.name());
        }
    }

    @Test
    void returnsEmptyWhenNoDockerComposeFound() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
