package com.archlens.backend.controller;

import com.archlens.backend.dto.*;
import com.archlens.backend.entity.*;
import com.archlens.backend.repository.*;
import com.archlens.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiDefinitionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RepoRepository repoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private GraphNodeRepository nodeRepository;
    @Autowired private GraphEdgeRepository edgeRepository;
    @Autowired private ApiDefinitionRepository apiDefinitionRepository;
    @Autowired private RequestExecutionRepository requestExecutionRepository;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private JwtService jwtService;

    private Repository publicRepo;
    private ArchitectureGraph publicGraph;
    private GraphNode serviceNode;
    private ApiDefinition apiDef1;
    private ApiDefinition apiDef2;

    @BeforeEach
    void setUp() {
        requestExecutionRepository.deleteAll();
        environmentRepository.deleteAll();
        apiDefinitionRepository.deleteAll();
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        publicRepo = new Repository();
        publicRepo.setGithubRepoId(555111L);
        publicRepo.setFullName("octocat/api-demo");
        publicRepo.setDefaultBranch("main");
        publicRepo.setAccessMethod("public_system_token");
        publicRepo = repoRepository.save(publicRepo);

        AnalysisJob job = new AnalysisJob(publicRepo, "main", "sha-api-001");
        job = jobRepository.save(job);

        publicGraph = new ArchitectureGraph(job, publicRepo, "sha-api-001");
        publicGraph = graphRepository.save(publicGraph);

        serviceNode = new GraphNode(
                publicGraph, "service:order-api", "order-api", "SERVICE", "Spring Boot", 0.85, "OrderController.java", "{}"
        );
        serviceNode = nodeRepository.save(serviceNode);

        apiDef1 = new ApiDefinition(
                publicGraph, serviceNode, "GET", "/api/v1/orders", "OrderController", "listOrders", "OrderController.java", 12, "{\"discovered\":true}"
        );
        apiDef1 = apiDefinitionRepository.save(apiDef1);

        apiDef2 = new ApiDefinition(
                publicGraph, serviceNode, "POST", "/api/v1/orders", "OrderController", "createOrder", "OrderController.java", 18, "{\"discovered\":true}"
        );
        apiDef2 = apiDefinitionRepository.save(apiDef2);
    }

    @Test
    void testGetDefinitionsForGraph_ReturnsAllApis() throws Exception {
        mockMvc.perform(get("/api/v1/graphs/" + publicGraph.getId() + "/api-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiDefinitions", hasSize(2)))
                .andExpect(jsonPath("$.apiDefinitions[0].path", is("/api/v1/orders")))
                .andExpect(jsonPath("$.apiDefinitions[0].graphNodeId", is(serviceNode.getId().toString())))
                .andExpect(jsonPath("$.apiDefinitions[0].discovered", is(true)));
    }

    @Test
    void testGetDefinition_ReturnsSingleApi() throws Exception {
        mockMvc.perform(get("/api/v1/api-definitions/" + apiDef1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(apiDef1.getId().toString())))
                .andExpect(jsonPath("$.method", is("GET")))
                .andExpect(jsonPath("$.path", is("/api/v1/orders")))
                .andExpect(jsonPath("$.handlerMethod", is("listOrders")))
                .andExpect(jsonPath("$.graphId", is(publicGraph.getId().toString())));
    }

    @Test
    void testCreateDefinition_Success() throws Exception {
        CreateApiDefinitionRequest req = new CreateApiDefinitionRequest(
                serviceNode.getId(),
                "DELETE",
                "/api/v1/orders/{id}",
                "bearer",
                Map.of("note", "manual deletion endpoint")
        );

        mockMvc.perform(post("/api/v1/api-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method", is("DELETE")))
                .andExpect(jsonPath("$.path", is("/api/v1/orders/{id}")))
                .andExpect(jsonPath("$.graphNodeId", is(serviceNode.getId().toString())))
                .andExpect(jsonPath("$.discovered", is(false)));
    }

    @Test
    void testCreateDefinition_MissingRequiredParams_Returns400() throws Exception {
        CreateApiDefinitionRequest req = new CreateApiDefinitionRequest(
                serviceNode.getId(),
                "",
                "/api/v1/orders",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/api-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("invalid_params")));
    }

    @Test
    void testGetDefinitions_PrivateRepo_RequiresOwnership() throws Exception {
        User owner = new User();
        owner.setGithubId(8881L);
        owner.setGithubUsername("apiOwner");
        owner = userRepository.save(owner);

        User attacker = new User();
        attacker.setGithubId(8882L);
        attacker.setGithubUsername("apiAttacker");
        attacker = userRepository.save(attacker);

        Repository privateRepo = new Repository();
        privateRepo.setUser(owner);
        privateRepo.setGithubRepoId(666111L);
        privateRepo.setFullName("apiOwner/private-api");
        privateRepo.setDefaultBranch("main");
        privateRepo.setAccessMethod("user_oauth");
        privateRepo = repoRepository.save(privateRepo);

        AnalysisJob privateJob = new AnalysisJob(privateRepo, "main", "sha-priv");
        privateJob = jobRepository.save(privateJob);

        ArchitectureGraph privateGraph = new ArchitectureGraph(privateJob, privateRepo, "sha-priv");
        privateGraph = graphRepository.save(privateGraph);

        // 1. Anonymous access -> 401
        mockMvc.perform(get("/api/v1/graphs/" + privateGraph.getId() + "/api-definitions"))
                .andExpect(status().isUnauthorized());

        // 2. Attacker access -> 403
        String attackerToken = jwtService.generateSessionToken(attacker);
        mockMvc.perform(get("/api/v1/graphs/" + privateGraph.getId() + "/api-definitions")
                        .cookie(new Cookie("archlens_session", attackerToken)))
                .andExpect(status().isForbidden());

        // 3. Owner access -> 200
        String ownerToken = jwtService.generateSessionToken(owner);
        mockMvc.perform(get("/api/v1/graphs/" + privateGraph.getId() + "/api-definitions")
                        .cookie(new Cookie("archlens_session", ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void testExecute_Success_WithEnvironmentVariableSubstitution() throws Exception {
        // Start a mock upstream server on an ephemeral port
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/orders/101", exchange -> {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            String response = "{\"status\":\"ok\",\"orderId\":101,\"auth\":\"" + authHeader + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        try {
            // Create user and environment with BASE_URL and AUTH_TOKEN
            User testUser = new User();
            testUser.setGithubId(77799L);
            testUser.setGithubUsername("tester");
            testUser = userRepository.save(testUser);

            String envJson = objectMapper.writeValueAsString(List.of(
                    new EnvironmentVariable("BASE_URL", "http://127.0.0.1:" + port, false),
                    new EnvironmentVariable("TOKEN", "Bearer test-token-123", false)
            ));
            Environment env = new Environment(testUser, "local-test", envJson);
            env = environmentRepository.save(env);

            // API definition with path parameter template: /api/v1/orders/{orderId}
            ApiDefinition parameterizedDef = new ApiDefinition(
                    publicGraph, serviceNode, "GET", "/api/v1/orders/{orderId}",
                    "OrderController", "getOrder", "OrderController.java", 25, "{}"
            );
            parameterizedDef = apiDefinitionRepository.save(parameterizedDef);

            ExecuteApiRequest execReq = new ExecuteApiRequest(
                    Map.of("Authorization", "{{TOKEN}}"),
                    Map.of("debug", "true"),
                    Map.of("orderId", "101"),
                    null,
                    env.getId()
            );

            String sessionToken = jwtService.generateSessionToken(testUser);

            mockMvc.perform(post("/api/v1/api-definitions/" + parameterizedDef.getId() + "/execute")
                            .cookie(new Cookie("archlens_session", sessionToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(execReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode", is(200)))
                    .andExpect(jsonPath("$.latencyMs", greaterThanOrEqualTo(0)))
                    .andExpect(jsonPath("$.responseBody.status", is("ok")))
                    .andExpect(jsonPath("$.responseBody.orderId", is(101)))
                    .andExpect(jsonPath("$.responseBody.auth", is("Bearer test-token-123")));

            // Verify execution was persisted in request_executions table
            List<RequestExecution> executions = requestExecutionRepository.findByApiDefinitionIdOrderByExecutedAtDesc(parameterizedDef.getId());
            assertEquals(1, executions.size());
            assertEquals(200, executions.get(0).getStatusCode());
            assertNull(executions.get(0).getError());
            assertNotNull(executions.get(0).getResponseBodyExcerpt());
            assertTrue(executions.get(0).getResponseBodyExcerpt().contains("\"orderId\":101"));

        } finally {
            server.stop(0);
        }
    }

    @Test
    void testExecute_UpstreamUnreachable_Returns502AndRecordsFailure() throws Exception {
        // Point to a port where nothing is listening
        ApiDefinition unreachableDef = new ApiDefinition(
                publicGraph, serviceNode, "GET", "http://127.0.0.1:59999/api/test",
                "TestController", "test", "TestController.java", 1, "{}"
        );
        unreachableDef = apiDefinitionRepository.save(unreachableDef);

        ExecuteApiRequest execReq = new ExecuteApiRequest(Map.of(), Map.of(), Map.of(), null, null);

        mockMvc.perform(post("/api/v1/api-definitions/" + unreachableDef.getId() + "/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(execReq)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code", is("upstream_unreachable")));

        // Verify failure was persisted in request_executions table
        List<RequestExecution> executions = requestExecutionRepository.findByApiDefinitionIdOrderByExecutedAtDesc(unreachableDef.getId());
        assertEquals(1, executions.size());
        assertEquals(502, executions.get(0).getStatusCode());
        assertNotNull(executions.get(0).getError());
        assertTrue(executions.get(0).getError().contains("upstream_"));
    }

    @Test
    void testGetExecutions_ReturnsPaginatedHistory() throws Exception {
        // Record two executions for apiDef1
        RequestExecution e1 = new RequestExecution(apiDef1, null, 200, 45, "{}", "{\"msg\":\"ok\"}", null);
        RequestExecution e2 = new RequestExecution(apiDef1, null, 500, 120, "{}", null, "server error");
        requestExecutionRepository.save(e1);
        requestExecutionRepository.save(e2);

        mockMvc.perform(get("/api/v1/api-definitions/" + apiDef1.getId() + "/executions?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].apiDefinitionId", is(apiDef1.getId().toString())));
    }
}

