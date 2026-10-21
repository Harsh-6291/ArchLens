package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateLoadTestRequest;
import com.archlens.backend.dto.LoadTestResponse;
import com.archlens.backend.dto.LoadTestResultResponse;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoadTestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LoadTestRepository loadTestRepository;
    @Autowired private LoadTestResultRepository loadTestResultRepository;
    @Autowired private ApiDefinitionRepository apiDefinitionRepository;
    @Autowired private GraphNodeRepository nodeRepository;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private RepoRepository repoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private User owner;
    private User other;
    private String ownerToken;
    private String otherToken;

    private Repository publicRepo;
    private ArchitectureGraph publicGraph;
    private GraphNode publicNode;
    private ApiDefinition publicApiDef;

    private Repository privateRepo;
    private ArchitectureGraph privateGraph;
    private GraphNode privateNode;
    private ApiDefinition privateApiDef;

    @BeforeEach
    void setUp() {
        loadTestResultRepository.deleteAll();
        loadTestRepository.deleteAll();
        apiDefinitionRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User();
        owner.setGithubId(501L);
        owner.setGithubUsername("load-owner");
        owner = userRepository.save(owner);
        ownerToken = jwtService.generateSessionToken(owner);

        other = new User();
        other.setGithubId(502L);
        other.setGithubUsername("load-other");
        other = userRepository.save(other);
        otherToken = jwtService.generateSessionToken(other);

        // Public repo + API definition
        publicRepo = new Repository();
        publicRepo.setGithubRepoId(600001L);
        publicRepo.setFullName("acme/public-service");
        publicRepo.setDefaultBranch("main");
        publicRepo.setAccessMethod("public_system_token");
        publicRepo = repoRepository.save(publicRepo);

        AnalysisJob pubJob = jobRepository.save(new AnalysisJob(publicRepo, "main", "sha-pub"));
        publicGraph = graphRepository.save(new ArchitectureGraph(pubJob, publicRepo, "sha-pub"));
        publicNode = nodeRepository.save(new GraphNode(publicGraph, "service:pub", "Pub", "SERVICE", "Spring Boot", 0.9, "Ctrl.java", "{}"));
        publicApiDef = apiDefinitionRepository.save(new ApiDefinition(publicGraph, publicNode, "GET", "/api/load-target", "Ctrl", "target", "Ctrl.java", 1, "{}"));

        // Private repo + API definition
        privateRepo = new Repository();
        privateRepo.setGithubRepoId(600002L);
        privateRepo.setFullName("load-owner/private-service");
        privateRepo.setDefaultBranch("main");
        privateRepo.setAccessMethod("user_oauth");
        privateRepo.setUser(owner);
        privateRepo = repoRepository.save(privateRepo);

        AnalysisJob privJob = jobRepository.save(new AnalysisJob(privateRepo, "main", "sha-priv"));
        privateGraph = graphRepository.save(new ArchitectureGraph(privJob, privateRepo, "sha-priv"));
        privateNode = nodeRepository.save(new GraphNode(privateGraph, "service:priv", "Priv", "SERVICE", "Spring Boot", 0.9, "Ctrl.java", "{}"));
        privateApiDef = apiDefinitionRepository.save(new ApiDefinition(privateGraph, privateNode, "GET", "/api/private-target", "Ctrl", "target", "Ctrl.java", 1, "{}"));
    }

    @Test
    void testCreateAndRun_PublicRepo_Succeeds() throws Exception {
        // Spin up local mock server
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/load-target", exchange -> {
            byte[] resp = "{\"status\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            publicApiDef.setPath("http://127.0.0.1:" + port + "/api/load-target");
            publicApiDef = apiDefinitionRepository.save(publicApiDef);

            CreateLoadTestRequest req = new CreateLoadTestRequest(
                    "smoke-test", null, "RAMP_UP", 5, 5, 1
            );

            String resJson = mockMvc.perform(post("/api/v1/api-definitions/" + publicApiDef.getId() + "/load-tests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.name", is("smoke-test")))
                    .andReturn().getResponse().getContentAsString();

            String testId = objectMapper.readTree(resJson).get("id").asText();

            // Fetch result
            mockMvc.perform(get("/api/v1/load-tests/" + testId + "/results"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRequests", greaterThan(0)))
                    .andExpect(jsonPath("$.successfulRequests", greaterThan(0)))
                    .andExpect(jsonPath("$.capacityThreshold", is("SAFE")));

        } finally {
            server.stop(0);
        }
    }

    @Test
    void testPrivateRepo_AuthRestrictions() throws Exception {
        CreateLoadTestRequest req = new CreateLoadTestRequest("private-test", null, "CONSTANT", 5, 5, 1);

        // Anonymous -> 401
        mockMvc.perform(post("/api/v1/api-definitions/" + privateApiDef.getId() + "/load-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // Other user -> 403
        mockMvc.perform(post("/api/v1/api-definitions/" + privateApiDef.getId() + "/load-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testListLoadTests_ReturnsSavedRuns() throws Exception {
        LoadTest lt = new LoadTest(publicApiDef, null, "run-1", "RAMP_UP", 5, 10, 2);
        loadTestRepository.save(lt);

        mockMvc.perform(get("/api/v1/api-definitions/" + publicApiDef.getId() + "/load-tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("run-1")));
    }
}
