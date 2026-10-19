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

class InfraConfigDetectorTest {

    private InfraConfigDetector detector;

    @BeforeEach
    void setUp() {
        detector = new InfraConfigDetector();
    }

    @Test
    void detectsInfraFromConfigurationFilesWithoutLeakingSecrets() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/infra-config").toAbsolutePath();
        assertTrue(detector.canDetect(fixturePath));

        List<DetectedNode> nodes = detector.detect(fixturePath);
        assertEquals(3, nodes.size());

        Map<String, DetectedNode> byType = nodes.stream()
                .collect(Collectors.toMap(DetectedNode::type, n -> n, (existing, replacement) -> existing));

        // 1. SQL Database
        DetectedNode dbNode = byType.get("SQL_DATABASE");
        assertNotNull(dbNode);
        assertEquals("PostgreSQL", dbNode.technology());
        assertEquals("order_service_db", dbNode.name());
        assertEquals(0.60, dbNode.confidence());

        // 2. Cache
        DetectedNode cacheNode = byType.get("CACHE");
        assertNotNull(cacheNode);
        assertEquals("Redis", cacheNode.technology());
        assertEquals(0.60, cacheNode.confidence());

        // 3. Message Broker
        DetectedNode mqNode = byType.get("MESSAGE_BROKER");
        assertNotNull(mqNode);
        assertEquals("RabbitMQ", mqNode.technology());
        assertEquals(0.60, mqNode.confidence());

        // Security check: Verify no secret passwords or tokens leaked
        for (DetectedNode node : nodes) {
            String repr = node.toString();
            assertFalse(repr.contains("super_secret_db_password_never_expose"), "Database password leaked");
            assertFalse(repr.contains("super_secret_redis_auth_token"), "Redis password leaked");
            assertFalse(repr.contains("super_secret_rmq_password"), "RabbitMQ password leaked");
        }
    }

    @Test
    void returnsEmptyWhenNoInfraConfigExists() {
        Path emptyPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(detector.canDetect(emptyPath));
        assertTrue(detector.detect(emptyPath).isEmpty());
    }
}
