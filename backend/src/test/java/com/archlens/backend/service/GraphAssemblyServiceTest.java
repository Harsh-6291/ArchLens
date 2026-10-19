package com.archlens.backend.service;

import com.archlens.backend.detector.model.DetectedNode;
import com.archlens.backend.detector.model.DetectionResult;
import com.archlens.backend.entity.*;
import com.archlens.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GraphAssemblyServiceTest {

    @Autowired
    private GraphAssemblyService graphAssemblyService;

    @Autowired
    private ArchitectureGraphRepository graphRepository;

    @Autowired
    private GraphNodeRepository nodeRepository;

    @Autowired
    private GraphEdgeRepository edgeRepository;

    @Autowired
    private AnalysisJobRepository jobRepository;

    @Autowired
    private RepoRepository repoRepository;

    private Repository repository;
    private AnalysisJob job;

    @BeforeEach
    void setUp() {
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();

        repository = new Repository();
        repository.setGithubRepoId(999001L);
        repository.setFullName("archlens/microservices-demo");
        repository.setDefaultBranch("main");
        repository.setAccessMethod("public_system_token");
        repository = repoRepository.save(repository);

        job = new AnalysisJob(repository, "main", "commit-abc-987");
        job = jobRepository.save(job);
    }

    @Test
    void testAssembleAndPersistGraph_ProducesExpectedNodesAndInferredEdges() {
        List<DetectedNode> nodes = List.of(
                new DetectedNode("web:frontend", "frontend-app", "WEB_APPLICATION", "React / Next.js", 0.40, "package.json", Map.of("framework", "Next.js")),
                new DetectedNode("service:order-api", "order-api", "SERVICE", "Spring Boot", 0.85, "pom.xml", Map.of("tier", 2)),
                new DetectedNode("database:orders-db", "orders-db", "SQL_DATABASE", "PostgreSQL", 0.60, "application.yml", Map.of("port", "5432")),
                new DetectedNode("cache:redis", "redis-cache", "CACHE", "Redis", 0.60, "application.yml", Map.of("port", "6379")),
                new DetectedNode("broker:rabbitmq", "rabbitmq-broker", "MESSAGE_BROKER", "RabbitMQ", 0.60, "application.yml", Map.of("port", "5672"))
        );
        DetectionResult result = new DetectionResult(nodes);

        ArchitectureGraph graph = graphAssemblyService.assembleAndPersistGraph(
                job.getId(), repository.getId(), "commit-abc-987", result
        );

        assertNotNull(graph);
        assertNotNull(graph.getId());
        assertEquals("commit-abc-987", graph.getCommitSha());

        // Verify Graph Nodes
        List<GraphNode> persistedNodes = nodeRepository.findByGraphId(graph.getId());
        assertEquals(5, persistedNodes.size());
        assertTrue(persistedNodes.stream().anyMatch(n -> "frontend-app".equals(n.getName()) && "WEB_APPLICATION".equals(n.getType())));
        assertTrue(persistedNodes.stream().anyMatch(n -> "order-api".equals(n.getName()) && "SERVICE".equals(n.getType()) && n.getConfidence() == 0.85));
        assertTrue(persistedNodes.stream().anyMatch(n -> "orders-db".equals(n.getName()) && "SQL_DATABASE".equals(n.getType())));
        assertTrue(persistedNodes.stream().anyMatch(n -> "redis-cache".equals(n.getName()) && "CACHE".equals(n.getType())));
        assertTrue(persistedNodes.stream().anyMatch(n -> "rabbitmq-broker".equals(n.getName()) && "MESSAGE_BROKER".equals(n.getType())));

        // Verify Graph Edges
        List<GraphEdge> persistedEdges = edgeRepository.findByGraphId(graph.getId());
        assertEquals(4, persistedEdges.size(), "Should infer: web->svc, svc->db, svc->cache, svc->broker");

        assertTrue(persistedEdges.stream().anyMatch(e ->
                "CALLS".equals(e.getEdgeType()) &&
                "frontend-app".equals(e.getSourceNode().getName()) &&
                "order-api".equals(e.getTargetNode().getName())
        ));

        assertTrue(persistedEdges.stream().anyMatch(e ->
                "CONNECTS_TO".equals(e.getEdgeType()) &&
                "order-api".equals(e.getSourceNode().getName()) &&
                "orders-db".equals(e.getTargetNode().getName())
        ));

        assertTrue(persistedEdges.stream().anyMatch(e ->
                "CONNECTS_TO".equals(e.getEdgeType()) &&
                "order-api".equals(e.getSourceNode().getName()) &&
                "redis-cache".equals(e.getTargetNode().getName())
        ));

        assertTrue(persistedEdges.stream().anyMatch(e ->
                "PUBLISHES_SUBSCRIBES".equals(e.getEdgeType()) &&
                "order-api".equals(e.getSourceNode().getName()) &&
                "rabbitmq-broker".equals(e.getTargetNode().getName())
        ));

        // Verify AnalysisJob was linked to the generated graphId
        AnalysisJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(graph.getId(), updatedJob.getGraphId());
    }

    @Test
    void testAssembleAndPersistGraph_EmptyNodes_PersistsEmptyGraph() {
        ArchitectureGraph graph = graphAssemblyService.assembleAndPersistGraph(
                job.getId(), repository.getId(), "commit-abc-987", new DetectionResult(List.of())
        );

        assertNotNull(graph);
        List<GraphNode> nodes = nodeRepository.findByGraphId(graph.getId());
        assertTrue(nodes.isEmpty());
        List<GraphEdge> edges = edgeRepository.findByGraphId(graph.getId());
        assertTrue(edges.isEmpty());
    }
}
