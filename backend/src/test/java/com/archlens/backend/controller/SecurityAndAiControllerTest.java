package com.archlens.backend.controller;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndAiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SecurityFindingRepository findingRepository;
    @Autowired private AiRecommendationRepository recommendationRepository;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private GraphNodeRepository nodeRepository;
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
    private GraphNode dbNode;

    private Repository privateRepo;
    private ArchitectureGraph privateGraph;

    @BeforeEach
    void setUp() {
        recommendationRepository.deleteAll();
        findingRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User();
        owner.setGithubId(701L);
        owner.setGithubUsername("sec-owner");
        owner = userRepository.save(owner);
        ownerToken = jwtService.generateSessionToken(owner);

        other = new User();
        other.setGithubId(702L);
        other.setGithubUsername("sec-other");
        other = userRepository.save(other);
        otherToken = jwtService.generateSessionToken(other);

        // Public repo + graph + DB node
        publicRepo = new Repository();
        publicRepo.setGithubRepoId(800001L);
        publicRepo.setFullName("acme/public-sec");
        publicRepo.setDefaultBranch("main");
        publicRepo.setAccessMethod("public_system_token");
        publicRepo = repoRepository.save(publicRepo);

        AnalysisJob pubJob = jobRepository.save(new AnalysisJob(publicRepo, "main", "sha-pub"));
        publicGraph = graphRepository.save(new ArchitectureGraph(pubJob, publicRepo, "sha-pub"));
        dbNode = nodeRepository.save(new GraphNode(publicGraph, "db:postgres", "Postgres", "SQL_DATABASE", "PostgreSQL", 0.95, "docker-compose.yml", "{}"));

        // Private repo
        privateRepo = new Repository();
        privateRepo.setGithubRepoId(800002L);
        privateRepo.setFullName("sec-owner/private-sec");
        privateRepo.setDefaultBranch("main");
        privateRepo.setAccessMethod("user_oauth");
        privateRepo.setUser(owner);
        privateRepo = repoRepository.save(privateRepo);

        AnalysisJob privJob = jobRepository.save(new AnalysisJob(privateRepo, "main", "sha-priv"));
        privateGraph = graphRepository.save(new ArchitectureGraph(privJob, privateRepo, "sha-priv"));
    }

    @Test
    void testSecurityScanAndRecommendations_PublicRepo() throws Exception {
        // Trigger scan
        mockMvc.perform(post("/api/v1/graphs/" + publicGraph.getId() + "/security-scan"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].severity", notNullValue()))
                .andExpect(jsonPath("$[0].evidenceId", startsWith("SEC-EVID-")));

        // Query findings
        mockMvc.perform(get("/api/v1/graphs/" + publicGraph.getId() + "/security-findings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        // Trigger AI recommendations
        mockMvc.perform(post("/api/v1/graphs/" + publicGraph.getId() + "/ai-recommendations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].problem", notNullValue()))
                .andExpect(jsonPath("$[0].hallucinationVerified", is(true)));

        // Query recommendations
        mockMvc.perform(get("/api/v1/graphs/" + publicGraph.getId() + "/ai-recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    void testPrivateRepo_SecurityScanRequiresOwnership() throws Exception {
        // Anonymous -> 401
        mockMvc.perform(post("/api/v1/graphs/" + privateGraph.getId() + "/security-scan"))
                .andExpect(status().isUnauthorized());

        // Other user -> 403
        mockMvc.perform(post("/api/v1/graphs/" + privateGraph.getId() + "/security-scan")
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, otherToken)))
                .andExpect(status().isForbidden());

        // Owner -> 201
        mockMvc.perform(post("/api/v1/graphs/" + privateGraph.getId() + "/security-scan")
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, ownerToken)))
                .andExpect(status().isCreated());
    }
}
