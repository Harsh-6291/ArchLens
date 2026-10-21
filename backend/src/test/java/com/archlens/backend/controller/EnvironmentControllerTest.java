package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateEnvironmentRequest;
import com.archlens.backend.dto.EnvironmentVariable;
import com.archlens.backend.dto.UpdateEnvironmentRequest;
import com.archlens.backend.entity.Environment;
import com.archlens.backend.entity.User;
import com.archlens.backend.repository.EnvironmentRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.JwtService;
import com.archlens.backend.service.EnvironmentService;
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
class EnvironmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EnvironmentService environmentService;
    @Autowired private JwtService jwtService;

    private User user1;
    private User user2;
    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() {
        environmentRepository.deleteAll();
        userRepository.deleteAll();

        user1 = new User();
        user1.setGithubId(101L);
        user1.setGithubUsername("alice");
        user1 = userRepository.save(user1);

        user2 = new User();
        user2.setGithubId(102L);
        user2.setGithubUsername("bob");
        user2 = userRepository.save(user2);

        user1Token = jwtService.generateSessionToken(user1);
        user2Token = jwtService.generateSessionToken(user2);
    }

    @Test
    void testCreateEnvironment_EncryptsSecretsAtRest_AndMasksInResponse() throws Exception {
        CreateEnvironmentRequest req = new CreateEnvironmentRequest(
                "staging",
                List.of(
                        new EnvironmentVariable("BASE_URL", "https://staging.archlens.io", false),
                        new EnvironmentVariable("AUTH_TOKEN", "super-secret-token-xyz-123", true)
                )
        );

        String responseBody = mockMvc.perform(post("/api/v1/environments")
                        .cookie(new Cookie("archlens_session", user1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("staging")))
                .andExpect(jsonPath("$.variables", hasSize(2)))
                .andExpect(jsonPath("$.variables[0].key", is("BASE_URL")))
                .andExpect(jsonPath("$.variables[0].value", is("https://staging.archlens.io")))
                .andExpect(jsonPath("$.variables[0].secret", is(false)))
                .andExpect(jsonPath("$.variables[1].key", is("AUTH_TOKEN")))
                .andExpect(jsonPath("$.variables[1].value", is(EnvironmentService.MASKED_SECRET)))
                .andExpect(jsonPath("$.variables[1].secret", is(true)))
                .andReturn().getResponse().getContentAsString();

        // 1. Verify encrypted at rest in Database
        Environment env = environmentRepository.findByUserId(user1.getId()).get(0);
        String rawDbJson = env.getVariables();
        assertNotNull(rawDbJson);
        assertFalse(rawDbJson.contains("super-secret-token-xyz-123"),
                "Plaintext secret must NEVER be stored unencrypted at rest");
        assertTrue(rawDbJson.contains("AUTH_TOKEN"));

        // 2. Verify decrypted only on use via EnvironmentService resolution
        Map<String, String> resolved = environmentService.getResolvedVariables(env.getId(), user1);
        assertEquals("https://staging.archlens.io", resolved.get("BASE_URL"));
        assertEquals("super-secret-token-xyz-123", resolved.get("AUTH_TOKEN"),
                "Secret variable must be decrypted correctly when resolved for execution");
    }

    @Test
    void testListAndGetEnvironments() throws Exception {
        CreateEnvironmentRequest req1 = new CreateEnvironmentRequest(
                "local",
                List.of(new EnvironmentVariable("HOST", "localhost", false))
        );
        CreateEnvironmentRequest req2 = new CreateEnvironmentRequest(
                "production",
                List.of(new EnvironmentVariable("SECRET_KEY", "prod-secret-999", true))
        );

        mockMvc.perform(post("/api/v1/environments")
                .cookie(new Cookie("archlens_session", user1Token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/environments")
                .cookie(new Cookie("archlens_session", user1Token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))).andExpect(status().isCreated());

        // List environments
        mockMvc.perform(get("/api/v1/environments")
                        .cookie(new Cookie("archlens_session", user1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("local")))
                .andExpect(jsonPath("$[1].name", is("production")))
                .andExpect(jsonPath("$[1].variables[0].value", is(EnvironmentService.MASKED_SECRET)));

        // Get single environment
        Environment env = environmentRepository.findByUserId(user1.getId()).stream()
                .filter(e -> "production".equals(e.getName())).findFirst().orElseThrow();

        mockMvc.perform(get("/api/v1/environments/" + env.getId())
                        .cookie(new Cookie("archlens_session", user1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(env.getId().toString())))
                .andExpect(jsonPath("$.name", is("production")));
    }

    @Test
    void testUpdateEnvironment_PreservesMaskedSecret() throws Exception {
        CreateEnvironmentRequest req = new CreateEnvironmentRequest(
                "dev",
                List.of(new EnvironmentVariable("API_KEY", "initial-secret-111", true))
        );

        mockMvc.perform(post("/api/v1/environments")
                .cookie(new Cookie("archlens_session", user1Token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());

        Environment env = environmentRepository.findByUserId(user1.getId()).get(0);

        // Update name and add a new var while keeping API_KEY masked
        UpdateEnvironmentRequest updateReq = new UpdateEnvironmentRequest(
                "dev-v2",
                List.of(
                        new EnvironmentVariable("API_KEY", EnvironmentService.MASKED_SECRET, true),
                        new EnvironmentVariable("PORT", "3000", false)
                )
        );

        mockMvc.perform(put("/api/v1/environments/" + env.getId())
                        .cookie(new Cookie("archlens_session", user1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("dev-v2")))
                .andExpect(jsonPath("$.variables", hasSize(2)));

        // Verify the original secret is still intact and decodable!
        Map<String, String> resolved = environmentService.getResolvedVariables(env.getId(), user1);
        assertEquals("initial-secret-111", resolved.get("API_KEY"));
        assertEquals("3000", resolved.get("PORT"));
    }

    @Test
    void testDeleteEnvironment_RemovesFromDb() throws Exception {
        CreateEnvironmentRequest req = new CreateEnvironmentRequest(
                "temp",
                List.of(new EnvironmentVariable("A", "B", false))
        );

        mockMvc.perform(post("/api/v1/environments")
                .cookie(new Cookie("archlens_session", user1Token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());

        Environment env = environmentRepository.findByUserId(user1.getId()).get(0);

        mockMvc.perform(delete("/api/v1/environments/" + env.getId())
                        .cookie(new Cookie("archlens_session", user1Token)))
                .andExpect(status().isNoContent());

        assertTrue(environmentRepository.findById(env.getId()).isEmpty());
    }

    @Test
    void testUserIsolation_UserCannotAccessAnotherUsersEnvironment() throws Exception {
        CreateEnvironmentRequest req = new CreateEnvironmentRequest(
                "alice-env",
                List.of(new EnvironmentVariable("SECRET", "alice-key", true))
        );

        mockMvc.perform(post("/api/v1/environments")
                .cookie(new Cookie("archlens_session", user1Token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());

        Environment aliceEnv = environmentRepository.findByUserId(user1.getId()).get(0);

        // Bob tries to access Alice's environment -> 404
        mockMvc.perform(get("/api/v1/environments/" + aliceEnv.getId())
                        .cookie(new Cookie("archlens_session", user2Token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("environment_not_found")));

        // Anonymous access -> 401
        mockMvc.perform(get("/api/v1/environments/" + aliceEnv.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("unauthorized")));
    }
}
