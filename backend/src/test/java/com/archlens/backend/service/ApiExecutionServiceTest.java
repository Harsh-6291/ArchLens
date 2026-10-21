package com.archlens.backend.service;

import com.archlens.backend.dto.ExecuteApiRequest;
import com.archlens.backend.entity.*;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class ApiExecutionServiceTest {

    @Autowired private ApiDefinitionRepository apiDefinitionRepository;
    @Autowired private RequestExecutionRepository requestExecutionRepository;
    @Autowired private EnvironmentService environmentService;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private GraphNodeRepository nodeRepository;
    @Autowired private RepoRepository repoRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private ObjectMapper objectMapper;

    private ApiDefinition apiDef;

    @BeforeEach
    void setUp() {
        requestExecutionRepository.deleteAll();
        apiDefinitionRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();

        Repository repo = new Repository();
        repo.setGithubRepoId(999222L);
        repo.setFullName("archlens/timeout-test");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        AnalysisJob job = new AnalysisJob(repo, "main", "sha-timeout");
        job = jobRepository.save(job);

        ArchitectureGraph graph = new ArchitectureGraph(job, repo, "sha-timeout");
        graph = graphRepository.save(graph);

        GraphNode node = new GraphNode(
                graph, "service:test-svc", "test-svc", "SERVICE", "Spring Boot", 0.85, "TestController.java", "{}"
        );
        node = nodeRepository.save(node);

        apiDef = new ApiDefinition(
                graph, node, "GET", "/api/v1/slow-endpoint", "TestController", "slow", "TestController.java", 10, "{}"
        );
        apiDef = apiDefinitionRepository.save(apiDef);
    }

    @Test
    void testExecute_UpstreamTimeout_Throws504AndPersistsRecord() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpTimeoutException("request timed out"));

        ApiExecutionService service = new ApiExecutionService(
                apiDefinitionRepository,
                requestExecutionRepository,
                environmentService,
                objectMapper,
                mockClient
        );

        ExecuteApiRequest req = new ExecuteApiRequest(Map.of(), Map.of(), Map.of(), null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.execute(apiDef.getId(), req, Optional.empty())
        );

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
        assertEquals("upstream_timeout", ex.getCode());

        // Verify failure was persisted in database
        List<RequestExecution> executions = requestExecutionRepository.findByApiDefinitionIdOrderByExecutedAtDesc(apiDef.getId());
        assertEquals(1, executions.size());
        assertEquals(504, executions.get(0).getStatusCode());
        assertEquals("upstream_timeout", executions.get(0).getError());
    }
}
