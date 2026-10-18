package com.archlens.backend.controller;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.dto.GitHubRepoDto;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.repository.RepoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RepoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepoRepository repoRepository;

    @MockBean
    private GitHubClient gitHubClient;

    @Test
    void testCreatePublicRepo_Success_Returns201() throws Exception {
        GitHubRepoDto fixtureDto = new GitHubRepoDto(
                1296269L,
                "Hello-World",
                "octocat/Hello-World",
                "master",
                false,
                "https://github.com/octocat/Hello-World",
                "My first repository"
        );
        when(gitHubClient.fetchRepoWithSystemToken(eq("octocat"), eq("Hello-World")))
                .thenReturn(fixtureDto);

        String requestJson = """
                {
                    "url": "https://github.com/octocat/Hello-World"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fullName", is("octocat/Hello-World")))
                .andExpect(jsonPath("$.defaultBranch", is("master")))
                .andExpect(jsonPath("$.accessMethod", is("public_system_token")))
                .andExpect(jsonPath("$.authorizedForTesting", is(false)));

        Optional<Repository> saved = repoRepository.findByFullName("octocat/Hello-World");
        assertTrue(saved.isPresent());
        assertNull(saved.get().getUser(), "Public repo import must have user_id null");
        assertEquals("public_system_token", saved.get().getAccessMethod());
        assertEquals("master", saved.get().getDefaultBranch());
    }

    @Test
    void testCreateRepo_MalformedUrl_Returns400() throws Exception {
        String requestJson = """
                {
                    "url": "https://gitlab.com/octocat/Hello-World"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("invalid_url")))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void testCreateRepo_InvalidUrlStructure_Returns400() throws Exception {
        String requestJson = """
                {
                    "url": "not-a-valid-url"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("invalid_url")));
    }

    @Test
    void testCreateRepo_NonExistentRepo_Returns404() throws Exception {
        when(gitHubClient.fetchRepoWithSystemToken(eq("archlens-non-existent-user"), eq("non-existent-repo-12345")))
                .thenThrow(new GitHubClient.GitHubClientException(404, "Repository not found on GitHub"));

        String requestJson = """
                {
                    "url": "https://github.com/archlens-non-existent-user/non-existent-repo-12345"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("repo_not_found")));
    }

    @Test
    void testCreateRepo_GitHubRateLimited_Returns429() throws Exception {
        when(gitHubClient.fetchRepoWithSystemToken(eq("rate-limited-user"), eq("rate-limited-repo")))
                .thenThrow(new GitHubClient.GitHubClientException(429, "API rate limit exceeded"));

        String requestJson = """
                {
                    "url": "https://github.com/rate-limited-user/rate-limited-repo"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code", is("github_rate_limited")));
    }
}
