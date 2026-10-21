package com.archlens.backend.controller;

import com.archlens.backend.dto.*;
import com.archlens.backend.entity.*;
import com.archlens.backend.repository.*;
import com.archlens.backend.security.JwtService;
import com.archlens.backend.service.GraphAssemblyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GraphControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RepoRepository repoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private GraphNodeRepository nodeRepository;
    @Autowired private GraphEdgeRepository edgeRepository;
    @Autowired private GraphAssemblyService graphAssemblyService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JwtService jwtService;

    private Repository publicRepo;
    private AnalysisJob publicJob;
    private ArchitectureGraph publicGraph;
    private GraphNode serviceNode;
    private GraphNode dbNode;

    @BeforeEach
    void setUp() {
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        // Clear redis keys matching archlens:*
        var keys = redisTemplate.keys("archlens:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        publicRepo = new Repository();
        publicRepo.setGithubRepoId(112233L);
        publicRepo.setFullName("octocat/caching-demo");
        publicRepo.setDefaultBranch("main");
        publicRepo.setAccessMethod("public_system_token");
        publicRepo = repoRepository.save(publicRepo);

        publicJob = new AnalysisJob(publicRepo, "main", "sha-graph-001");
        publicJob = jobRepository.save(publicJob);

        publicGraph = new ArchitectureGraph(publicJob, publicRepo, "sha-graph-001");
        publicGraph = graphRepository.save(publicGraph);

        publicJob.setGraphId(publicGraph.getId());
        publicJob = jobRepository.save(publicJob);

        serviceNode = new GraphNode(
                publicGraph, "service:order-api", "order-api", "SERVICE", "Spring Boot", 0.85, "pom.xml", "{\"tier\":2}"
        );
        serviceNode = nodeRepository.save(serviceNode);

        dbNode = new GraphNode(
                publicGraph, "database:order-db", "order-db", "SQL_DATABASE", "PostgreSQL", 0.60, "application.yml", "{}"
        );
        dbNode = nodeRepository.save(dbNode);

        GraphEdge edge = new GraphEdge(
                publicGraph, serviceNode, dbNode, "CONNECTS_TO", 0.70, "{\"inferred\":true}"
        );
        edgeRepository.save(edge);
    }

    @Test
    void testGetGraph_FirstCallMissesCache_SecondCallServedFromCache() throws Exception {
        UUID graphId = publicGraph.getId();
        String redisKey = "archlens:graph:" + graphId;

        // Ensure key does not exist yet in Redis
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)), "Cache should be empty initially");

        // 1. First Call: Cache MISS -> loads from DB and stores into Redis
        mockMvc.perform(get("/api/v1/graphs/" + graphId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graphId", is(graphId.toString())))
                .andExpect(jsonPath("$.commitSha", is("sha-graph-001")))
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[0].type", is("CONNECTS_TO")));

        // Verify it was cached in Redis
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)), "Graph should be cached in Redis after first call");
        String cachedJson = redisTemplate.opsForValue().get(redisKey);
        assertNotNull(cachedJson);
        assertTrue(cachedJson.contains("order-api"));

        // 2. Second Call: Cache HIT -> served from Redis
        mockMvc.perform(get("/api/v1/graphs/" + graphId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graphId", is(graphId.toString())))
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.edges", hasSize(1)));
    }

    @Test
    void testGetGraph_CacheInvalidatedOnNewAnalysisOfSameRepo() throws Exception {
        UUID graphId = publicGraph.getId();
        String redisKey = "archlens:graph:" + graphId;

        // Initial call caches the graph in Redis
        mockMvc.perform(get("/api/v1/graphs/" + graphId))
                .andExpect(status().isOk());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        // Create a new analysis job and assemble a new graph for the same repository
        AnalysisJob newJob = new AnalysisJob(publicRepo, "main", "sha-graph-002");
        newJob = jobRepository.save(newJob);

        graphAssemblyService.assembleAndPersistGraph(
                newJob.getId(), publicRepo.getId(), "sha-graph-002", null
        );

        // Verify previous cache was invalidated
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)),
                "Previous cached graph must be evicted upon new analysis of the same repository");
    }

    @Test
    void testGetGraph_PrivateRepo_RequiresAuthAndOwnership() throws Exception {
        User owner = new User();
        owner.setGithubId(111L);
        owner.setGithubUsername("graphOwner");
        owner = userRepository.save(owner);

        User attacker = new User();
        attacker.setGithubId(222L);
        attacker.setGithubUsername("graphAttacker");
        attacker = userRepository.save(attacker);

        Repository privateRepo = new Repository();
        privateRepo.setUser(owner);
        privateRepo.setGithubRepoId(99999L);
        privateRepo.setFullName("graphOwner/secret-graph-repo");
        privateRepo.setDefaultBranch("main");
        privateRepo.setAccessMethod("user_oauth");
        privateRepo = repoRepository.save(privateRepo);

        AnalysisJob privateJob = new AnalysisJob(privateRepo, "main", "sha-private");
        privateJob = jobRepository.save(privateJob);

        ArchitectureGraph privateGraph = new ArchitectureGraph(privateJob, privateRepo, "sha-private");
        privateGraph = graphRepository.save(privateGraph);

        // 1. Anonymous access -> 401
        mockMvc.perform(get("/api/v1/graphs/" + privateGraph.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("unauthorized")));

        // 2. Non-owner (attacker) access -> 403
        String attackerToken = jwtService.generateSessionToken(attacker);
        mockMvc.perform(get("/api/v1/graphs/" + privateGraph.getId())
                        .cookie(new Cookie("archlens_session", attackerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("not_owner")));

        // 3. Owner access -> 200
        String ownerToken = jwtService.generateSessionToken(owner);
        mockMvc.perform(get("/api/v1/graphs/" + privateGraph.getId())
                        .cookie(new Cookie("archlens_session", ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graphId", is(privateGraph.getId().toString())));
    }

    @Test
    void testGetGraph_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/graphs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("graph_not_found")));
    }

    @Test
    void testPatchNode_UpdatesNodeAndEvictsCache() throws Exception {
        UUID graphId = publicGraph.getId();
        UUID nodeId = serviceNode.getId();
        String redisKey = "archlens:graph:" + graphId;

        // Warm cache first
        mockMvc.perform(get("/api/v1/graphs/" + graphId)).andExpect(status().isOk());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        PatchNodeRequest patchReq = new PatchNodeRequest("order-api-v2", "API_GATEWAY", "Spring Cloud Gateway", "gateway/pom.xml");
        mockMvc.perform(patch("/api/v1/graphs/" + graphId + "/nodes/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(nodeId.toString())))
                .andExpect(jsonPath("$.name", is("order-api-v2")))
                .andExpect(jsonPath("$.type", is("API_GATEWAY")))
                .andExpect(jsonPath("$.technology", is("Spring Cloud Gateway")))
                .andExpect(jsonPath("$.sourceFile", is("gateway/pom.xml")));

        // Cache must be evicted
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        // Verify DB update persisted
        GraphNode updatedNode = nodeRepository.findById(nodeId).orElseThrow();
        assertEquals("order-api-v2", updatedNode.getName());
        assertEquals("API_GATEWAY", updatedNode.getType());
    }

    @Test
    void testDeleteNode_DeletesNodeAndIncidentEdgesAndEvictsCache() throws Exception {
        UUID graphId = publicGraph.getId();
        UUID nodeId = serviceNode.getId();
        String redisKey = "archlens:graph:" + graphId;

        // Warm cache
        mockMvc.perform(get("/api/v1/graphs/" + graphId)).andExpect(status().isOk());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        mockMvc.perform(delete("/api/v1/graphs/" + graphId + "/nodes/" + nodeId))
                .andExpect(status().isNoContent());

        // Cache must be evicted
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        // Node and incident edge deleted
        assertTrue(nodeRepository.findById(nodeId).isEmpty());
        assertTrue(edgeRepository.findByGraphId(graphId).isEmpty());
    }

    @Test
    void testCreateEdge_AddsEdgeAndEvictsCache() throws Exception {
        UUID graphId = publicGraph.getId();
        String redisKey = "archlens:graph:" + graphId;

        // Warm cache
        mockMvc.perform(get("/api/v1/graphs/" + graphId)).andExpect(status().isOk());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        CreateEdgeRequest edgeReq = new CreateEdgeRequest(dbNode.getId(), serviceNode.getId(), "REPLIES_TO");
        mockMvc.perform(post("/api/v1/graphs/" + graphId + "/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edgeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source", is(dbNode.getId().toString())))
                .andExpect(jsonPath("$.target", is(serviceNode.getId().toString())))
                .andExpect(jsonPath("$.type", is("REPLIES_TO")));

        // Cache evicted
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        // Edge count now 2 in DB
        assertEquals(2, edgeRepository.findByGraphId(graphId).size());
    }

    @Test
    void testDeleteEdge_DeletesEdgeAndEvictsCache() throws Exception {
        UUID graphId = publicGraph.getId();
        GraphEdge edge = edgeRepository.findByGraphId(graphId).get(0);
        UUID edgeId = edge.getId();
        String redisKey = "archlens:graph:" + graphId;

        // Warm cache
        mockMvc.perform(get("/api/v1/graphs/" + graphId)).andExpect(status().isOk());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        mockMvc.perform(delete("/api/v1/graphs/" + graphId + "/edges/" + edgeId))
                .andExpect(status().isNoContent());

        // Cache evicted
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        // Edge deleted from DB
        assertTrue(edgeRepository.findById(edgeId).isEmpty());
    }
}
