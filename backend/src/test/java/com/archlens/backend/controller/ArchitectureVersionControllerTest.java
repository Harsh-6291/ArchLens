package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateVersionRequest;
import com.archlens.backend.dto.GraphDiffResponse;
import com.archlens.backend.entity.*;
import com.archlens.backend.repository.*;
import com.archlens.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureVersionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ArchitectureVersionRepository versionRepository;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private GraphNodeRepository nodeRepository;
    @Autowired private GraphEdgeRepository edgeRepository;
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
    private GraphNode node1;
    private GraphNode node2;

    private Repository privateRepo;
    private ArchitectureGraph privateGraph;

    @BeforeEach
    void setUp() {
        versionRepository.deleteAll();
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User();
        owner.setGithubId(901L);
        owner.setGithubUsername("ver-owner");
        owner = userRepository.save(owner);
        ownerToken = jwtService.generateSessionToken(owner);

        other = new User();
        other.setGithubId(902L);
        other.setGithubUsername("ver-other");
        other = userRepository.save(other);
        otherToken = jwtService.generateSessionToken(other);

        // Public repo + graph
        publicRepo = new Repository();
        publicRepo.setGithubRepoId(900001L);
        publicRepo.setFullName("acme/public-versions");
        publicRepo.setDefaultBranch("main");
        publicRepo.setAccessMethod("public_system_token");
        publicRepo = repoRepository.save(publicRepo);

        AnalysisJob pubJob = jobRepository.save(new AnalysisJob(publicRepo, "main", "sha-pub"));
        publicGraph = graphRepository.save(new ArchitectureGraph(pubJob, publicRepo, "sha-pub"));
        node1 = nodeRepository.save(new GraphNode(publicGraph, "service:order", "OrderService", "SERVICE", "Spring Boot", 0.9, "Order.java", "{}"));
        node2 = nodeRepository.save(new GraphNode(publicGraph, "db:postgres", "PostgresDB", "SQL_DATABASE", "PostgreSQL", 0.95, "docker.yml", "{}"));
        edgeRepository.save(new GraphEdge(publicGraph, node1, node2, "CONNECTS_TO", 0.9, "{}"));

        // Private repo + graph
        privateRepo = new Repository();
        privateRepo.setGithubRepoId(900002L);
        privateRepo.setFullName("ver-owner/private-versions");
        privateRepo.setDefaultBranch("main");
        privateRepo.setAccessMethod("user_oauth");
        privateRepo.setUser(owner);
        privateRepo = repoRepository.save(privateRepo);

        AnalysisJob privJob = jobRepository.save(new AnalysisJob(privateRepo, "main", "sha-priv"));
        privateGraph = graphRepository.save(new ArchitectureGraph(privJob, privateRepo, "sha-priv"));
    }

    @Test
    void testCreateVersionAndCompareDiff_PublicRepo() throws Exception {
        CreateVersionRequest req = new CreateVersionRequest("v1.0.0", "Initial baseline architecture");

        // 1. Create snapshot
        String resJson = mockMvc.perform(post("/api/v1/graphs/" + publicGraph.getId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionName", is("v1.0.0")))
                .andReturn().getResponse().getContentAsString();

        String versionId = objectMapper.readTree(resJson).get("id").asText();

        // 2. Add a new node to the current graph to simulate changes
        GraphNode node3 = nodeRepository.save(new GraphNode(publicGraph, "cache:redis", "RedisCache", "CACHE", "Redis", 0.88, "app.yml", "{}"));

        // 3. Compare current against snapshot
        mockMvc.perform(get("/api/v1/graphs/" + publicGraph.getId() + "/compare?targetVersionId=" + versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionName", is("v1.0.0")))
                .andExpect(jsonPath("$.addedNodes", hasSize(1)))
                .andExpect(jsonPath("$.addedNodes[0].nodeKey", is("cache:redis")))
                .andExpect(jsonPath("$.removedNodes", empty()));
    }

    @Test
    void testPrivateRepo_RequiresOwnership() throws Exception {
        CreateVersionRequest req = new CreateVersionRequest("v1.0.0-priv", "Snapshot");

        // Anonymous -> 401
        mockMvc.perform(post("/api/v1/graphs/" + privateGraph.getId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // Other user -> 403
        mockMvc.perform(post("/api/v1/graphs/" + privateGraph.getId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, otherToken)))
                .andExpect(status().isForbidden());

        // Owner -> 201
        mockMvc.perform(post("/api/v1/graphs/" + privateGraph.getId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, ownerToken)))
                .andExpect(status().isCreated());
    }
}
