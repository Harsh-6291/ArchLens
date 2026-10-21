package com.archlens.backend.service;

import com.archlens.backend.entity.*;
import com.archlens.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApiExtractionServiceTest {

    @Autowired
    private ApiExtractionService apiExtractionService;

    @Autowired
    private ApiDefinitionRepository apiDefinitionRepository;

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

    private Repository repo;
    private AnalysisJob job;
    private ArchitectureGraph graph;
    private GraphNode serviceNode;

    @BeforeEach
    void setUp() {
        apiDefinitionRepository.deleteAll();
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();

        repo = new Repository();
        repo.setGithubRepoId(777111L);
        repo.setFullName("archlens/api-demo");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        job = new AnalysisJob(repo, "main", "sha-api-001");
        job = jobRepository.save(job);

        graph = new ArchitectureGraph(job, repo, "sha-api-001");
        graph = graphRepository.save(graph);

        String serviceMetadata = """
                {
                  "tier": 2,
                  "framework": "Spring Boot",
                  "routes": [
                    {
                      "httpMethod": "GET",
                      "path": "/api/v1/orders",
                      "handlerClass": "OrderController",
                      "handlerMethod": "listOrders",
                      "sourceFile": "OrderController.java",
                      "lineNumber": 12
                    },
                    {
                      "httpMethod": "POST",
                      "path": "/api/v1/orders",
                      "handlerClass": "OrderController",
                      "handlerMethod": "createOrder",
                      "sourceFile": "OrderController.java",
                      "lineNumber": 18
                    }
                  ]
                }
                """;

        serviceNode = new GraphNode(
                graph, "service:order-api", "order-api", "SERVICE", "Spring Boot", 0.85, "OrderController.java", serviceMetadata
        );
        serviceNode = nodeRepository.save(serviceNode);

        // Also add a non-SERVICE node (database) to verify it is ignored
        GraphNode dbNode = new GraphNode(
                graph, "database:order-db", "order-db", "SQL_DATABASE", "PostgreSQL", 0.60, "application.yml", "{\"routes\": []}"
        );
        nodeRepository.save(dbNode);
    }

    @Test
    void testExtractAndPersist_ExtractsRoutesFromServiceNodeMetadata() {
        int count = apiExtractionService.extractAndPersist(graph);
        assertEquals(2, count);

        List<ApiDefinition> definitions = apiDefinitionRepository.findByGraphId(graph.getId());
        assertEquals(2, definitions.size());

        ApiDefinition getRoute = definitions.stream()
                .filter(d -> "GET".equals(d.getHttpMethod()))
                .findFirst()
                .orElseThrow();
        assertEquals("/api/v1/orders", getRoute.getPath());
        assertEquals("OrderController", getRoute.getHandlerClass());
        assertEquals("listOrders", getRoute.getHandlerMethod());
        assertEquals("OrderController.java", getRoute.getSourceFile());
        assertEquals(12, getRoute.getLineNumber());
        assertEquals(serviceNode.getId(), getRoute.getGraphNode().getId());
        assertEquals(graph.getId(), getRoute.getGraph().getId());

        ApiDefinition postRoute = definitions.stream()
                .filter(d -> "POST".equals(d.getHttpMethod()))
                .findFirst()
                .orElseThrow();
        assertEquals("/api/v1/orders", postRoute.getPath());
        assertEquals("createOrder", postRoute.getHandlerMethod());
        assertEquals(18, postRoute.getLineNumber());
    }

    @Test
    void testExtractAndPersist_IsIdempotent_ReplacesExistingDefinitions() {
        // Run first time
        int count1 = apiExtractionService.extractAndPersist(graph);
        assertEquals(2, count1);
        assertEquals(2, apiDefinitionRepository.findByGraphId(graph.getId()).size());

        // Run second time on same graph
        int count2 = apiExtractionService.extractAndPersist(graph);
        assertEquals(2, count2);
        // Must still have exactly 2, not 4
        assertEquals(2, apiDefinitionRepository.findByGraphId(graph.getId()).size());
    }

    @Test
    void testExtractAndPersist_NoRoutes_ReturnsZero() {
        ArchitectureGraph emptyGraph = graphRepository.save(new ArchitectureGraph(job, repo, "sha-empty"));
        int count = apiExtractionService.extractAndPersist(emptyGraph);
        assertEquals(0, count);
        assertTrue(apiDefinitionRepository.findByGraphId(emptyGraph.getId()).isEmpty());
    }
}
