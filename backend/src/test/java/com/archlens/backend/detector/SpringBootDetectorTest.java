package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpringBootDetectorTest {

    private SpringBootDetector springBootDetector;
    private MavenDetector mavenDetector;

    @BeforeEach
    void setUp() {
        springBootDetector = new SpringBootDetector();
        mavenDetector = new MavenDetector();
    }

    @Test
    void detectsSpringBootApplicationWithLayeredMetadataAndHighConfidence() {
        Path fixturePath = Paths.get("src/test/resources/fixtures/spring-boot").toAbsolutePath();
        assertTrue(springBootDetector.canDetect(fixturePath));

        List<DetectedNode> nodes = springBootDetector.detect(fixturePath);
        assertEquals(1, nodes.size(), "Should detect single unified Spring Boot service");

        DetectedNode node = nodes.get(0);
        assertEquals("SERVICE", node.type());
        assertEquals("Spring Boot", node.technology());
        assertEquals(0.85, node.confidence());

        // Compare with Tier 1 output on the exact same fixture
        List<DetectedNode> tier1Nodes = mavenDetector.detect(fixturePath);
        assertFalse(tier1Nodes.isEmpty());
        DetectedNode tier1Node = tier1Nodes.get(0);
        assertEquals(0.40, tier1Node.confidence());
        assertTrue(node.confidence() > tier1Node.confidence(),
                "Tier 2 Spring Boot confidence (0.85) must be higher than Tier 1 confidence (0.40)");

        // Verify layered architecture metadata
        Map<String, Object> metadata = node.metadata();
        assertEquals(2, metadata.get("tier"));
        assertEquals("Spring Boot", metadata.get("framework"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> controllers = (List<Map<String, Object>>) metadata.get("controllers");
        assertNotNull(controllers);
        assertEquals(2, controllers.size());
        assertTrue(controllers.stream().anyMatch(c -> "UserController".equals(c.get("className")) && "/api/v1/users".equals(c.get("baseRoute"))));
        assertTrue(controllers.stream().anyMatch(c -> "OrderController".equals(c.get("className")) && "/api/v1/orders".equals(c.get("baseRoute"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) metadata.get("services");
        assertNotNull(services);
        assertEquals(2, services.size());
        assertTrue(services.stream().anyMatch(s -> "UserService".equals(s.get("className"))));
        assertTrue(services.stream().anyMatch(s -> "OrderService".equals(s.get("className"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> repositories = (List<Map<String, Object>>) metadata.get("repositories");
        assertNotNull(repositories);
        assertEquals(2, repositories.size());
        assertTrue(repositories.stream().anyMatch(r -> "UserRepository".equals(r.get("className"))));
        assertTrue(repositories.stream().anyMatch(r -> "OrderRepository".equals(r.get("className"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> layerSummary = (Map<String, Object>) metadata.get("layerSummary");
        assertNotNull(layerSummary);
        assertEquals(2, layerSummary.get("controllerCount"));
        assertEquals(2, layerSummary.get("serviceCount"));
        assertEquals(2, layerSummary.get("repositoryCount"));
        assertEquals(2, layerSummary.get("routeCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>) metadata.get("routes");
        assertNotNull(routes);
        assertEquals(2, routes.size());
        assertTrue(routes.stream().anyMatch(r ->
                "GET".equals(r.get("httpMethod"))
                && "/api/v1/users".equals(r.get("path"))
                && "listUsers".equals(r.get("handlerMethod"))
                && "UserController".equals(r.get("handlerClass"))));
        assertTrue(routes.stream().anyMatch(r ->
                "GET".equals(r.get("httpMethod"))
                && "/api/v1/orders".equals(r.get("path"))
                && "listOrders".equals(r.get("handlerMethod"))
                && "OrderController".equals(r.get("handlerClass"))));
    }

    @Test
    void returnsEmptyOnNonSpringBootProjects() {
        Path nodejsPath = Paths.get("src/test/resources/fixtures/nodejs").toAbsolutePath();
        assertFalse(springBootDetector.canDetect(nodejsPath));
        assertTrue(springBootDetector.detect(nodejsPath).isEmpty());

        Path pureMavenPath = Paths.get("src/test/resources/fixtures/maven").toAbsolutePath();
        assertFalse(springBootDetector.canDetect(pureMavenPath));
        assertTrue(springBootDetector.detect(pureMavenPath).isEmpty());
    }
}
