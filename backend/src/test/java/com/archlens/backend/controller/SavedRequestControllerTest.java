package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateSavedRequestRequest;
import com.archlens.backend.dto.UpdateSavedRequestRequest;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SavedRequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SavedRequestRepository savedRequestRepository;
    @Autowired private ApiDefinitionRepository apiDefinitionRepository;
    @Autowired private ArchitectureGraphRepository graphRepository;
    @Autowired private GraphNodeRepository nodeRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private RepoRepository repoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private ApiDefinition publicApiDef;
    private ApiDefinition privateApiDef;
    private User owner;
    private User other;
    private String ownerToken;
    private String otherToken;
    private String publicBase;
    private String privateBase;

    @BeforeEach
    void setUp() {
        savedRequestRepository.deleteAll();
        apiDefinitionRepository.deleteAll();
        nodeRepository.deleteAll();
        graphRepository.deleteAll();
        jobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        // Users
        owner = new User();
        owner.setGithubId(301L);
        owner.setGithubUsername("owner-user");
        owner = userRepository.save(owner);
        ownerToken = jwtService.generateSessionToken(owner);

        other = new User();
        other.setGithubId(302L);
        other.setGithubUsername("other-user");
        other = userRepository.save(other);
        otherToken = jwtService.generateSessionToken(other);

        // Public repo + graph + api def
        Repository publicRepo = new Repository();
        publicRepo.setGithubRepoId(400001L);
        publicRepo.setFullName("acme/public-svc");
        publicRepo.setDefaultBranch("main");
        publicRepo.setAccessMethod("public_system_token");
        publicRepo = repoRepository.save(publicRepo);

        AnalysisJob publicJob = new AnalysisJob(publicRepo, "main", "sha-pub");
        publicJob = jobRepository.save(publicJob);

        ArchitectureGraph publicGraph = new ArchitectureGraph(publicJob, publicRepo, "sha-pub");
        publicGraph = graphRepository.save(publicGraph);

        GraphNode publicNode = new GraphNode(publicGraph, "service:pub", "pub", "SERVICE", "Spring Boot", 0.9, "Ctrl.java", "{}");
        publicNode = nodeRepository.save(publicNode);

        publicApiDef = new ApiDefinition(publicGraph, publicNode, "GET", "/api/items", "Ctrl", "list", "Ctrl.java", 10, "{}");
        publicApiDef = apiDefinitionRepository.save(publicApiDef);
        publicBase = "/api/v1/api-definitions/" + publicApiDef.getId() + "/saved-requests";

        // Private repo + graph + api def (owned by `owner`)
        Repository privateRepo = new Repository();
        privateRepo.setGithubRepoId(400002L);
        privateRepo.setFullName("owner-user/private-svc");
        privateRepo.setDefaultBranch("main");
        privateRepo.setAccessMethod("user_oauth");
        privateRepo.setUser(owner);
        privateRepo = repoRepository.save(privateRepo);

        AnalysisJob privateJob = new AnalysisJob(privateRepo, "main", "sha-priv");
        privateJob = jobRepository.save(privateJob);

        ArchitectureGraph privateGraph = new ArchitectureGraph(privateJob, privateRepo, "sha-priv");
        privateGraph = graphRepository.save(privateGraph);

        GraphNode privateNode = new GraphNode(privateGraph, "service:priv", "priv", "SERVICE", "Spring Boot", 0.8, "Ctrl.java", "{}");
        privateNode = nodeRepository.save(privateNode);

        privateApiDef = new ApiDefinition(privateGraph, privateNode, "POST", "/api/private", "Ctrl", "create", "Ctrl.java", 20, "{}");
        privateApiDef = apiDefinitionRepository.save(privateApiDef);
        privateBase = "/api/v1/api-definitions/" + privateApiDef.getId() + "/saved-requests";
    }

    // ------------------------------------------- CREATE

    @Test
    void testCreate_PublicRepo_AnonymousUser_Returns201() throws Exception {
        CreateSavedRequestRequest req = new CreateSavedRequestRequest(
                "happy-path", "A simple GET request",
                Map.of(), Map.of("limit", "10"), Map.of("Accept", "application/json"),
                null, null
        );

        mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name", is("happy-path")))
                .andExpect(jsonPath("$.queryParams.limit", is("10")))
                .andExpect(jsonPath("$.apiDefinitionId", is(publicApiDef.getId().toString())));
    }

    @Test
    void testCreate_MissingName_Returns400() throws Exception {
        CreateSavedRequestRequest req = new CreateSavedRequestRequest(
                "", null, Map.of(), Map.of(), Map.of(), null, null
        );

        mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreate_DuplicateName_Returns409() throws Exception {
        CreateSavedRequestRequest req = new CreateSavedRequestRequest(
                "duplicate", null, Map.of(), Map.of(), Map.of(), null, null
        );

        mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("saved_request_name_conflict")));
    }

    @Test
    void testCreate_PrivateRepo_RequiresOwner() throws Exception {
        CreateSavedRequestRequest req = new CreateSavedRequestRequest(
                "private-req", null, Map.of(), Map.of(), Map.of(), null, null
        );

        // Anonymous → 401
        mockMvc.perform(post(privateBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // Other user → 403
        mockMvc.perform(post(privateBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, otherToken)))
                .andExpect(status().isForbidden());

        // Owner → 201
        mockMvc.perform(post(privateBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, ownerToken)))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------- LIST

    @Test
    void testList_ReturnsSavedRequestsInOrder() throws Exception {
        CreateSavedRequestRequest r1 = new CreateSavedRequestRequest("first", null, Map.of(), Map.of(), Map.of(), null, null);
        CreateSavedRequestRequest r2 = new CreateSavedRequestRequest("second", null, Map.of(), Map.of(), Map.of(), null, null);

        mockMvc.perform(post(publicBase).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(r1)));
        mockMvc.perform(post(publicBase).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(r2)));

        mockMvc.perform(get(publicBase))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("first")))
                .andExpect(jsonPath("$[1].name", is("second")));
    }

    // -------------------------------------------- GET

    @Test
    void testGet_NotFound_Returns404() throws Exception {
        mockMvc.perform(get(publicBase + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGet_WrongParent_Returns404() throws Exception {
        // Create a saved request under publicApiDef, then try to retrieve it via privateApiDef path
        CreateSavedRequestRequest req = new CreateSavedRequestRequest("cross-parent", null, Map.of(), Map.of(), Map.of(), null, null);

        String responseJson = mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(responseJson).get("id").asText();

        // Retrieve via privateApiDef → 404 (wrong parent)
        mockMvc.perform(get(privateBase + "/" + id)
                        .cookie(new Cookie(JwtService.SESSION_COOKIE_NAME, ownerToken)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------- UPDATE

    @Test
    void testUpdate_PatchesFields() throws Exception {
        CreateSavedRequestRequest create = new CreateSavedRequestRequest("update-me", "original", Map.of(), Map.of(), Map.of(), null, null);
        String createJson = mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createJson).get("id").asText();

        UpdateSavedRequestRequest update = new UpdateSavedRequestRequest(
                "update-me-renamed", "updated desc",
                Map.of("id", "42"), null, null, null, null
        );

        mockMvc.perform(patch(publicBase + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("update-me-renamed")))
                .andExpect(jsonPath("$.description", is("updated desc")))
                .andExpect(jsonPath("$.pathParams.id", is("42")));
    }

    @Test
    void testUpdate_NameConflict_Returns409() throws Exception {
        // Create two requests, then try to rename second to first's name
        mockMvc.perform(post(publicBase).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSavedRequestRequest("name-a", null, Map.of(), Map.of(), Map.of(), null, null))));
        String b = mockMvc.perform(post(publicBase).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSavedRequestRequest("name-b", null, Map.of(), Map.of(), Map.of(), null, null))))
                .andReturn().getResponse().getContentAsString();
        String bId = objectMapper.readTree(b).get("id").asText();

        mockMvc.perform(patch(publicBase + "/" + bId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSavedRequestRequest("name-a", null, null, null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("saved_request_name_conflict")));
    }

    // -------------------------------------------- DELETE

    @Test
    void testDelete_RemovesSavedRequest() throws Exception {
        CreateSavedRequestRequest req = new CreateSavedRequestRequest("to-delete", null, Map.of(), Map.of(), Map.of(), null, null);
        String createJson = mockMvc.perform(post(publicBase)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createJson).get("id").asText();

        mockMvc.perform(delete(publicBase + "/" + id))
                .andExpect(status().isNoContent());

        assertFalse(savedRequestRepository.existsById(UUID.fromString(id)));
    }

    @Test
    void testDelete_NotFound_Returns404() throws Exception {
        mockMvc.perform(delete(publicBase + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
