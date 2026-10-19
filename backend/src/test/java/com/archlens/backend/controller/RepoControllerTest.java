package com.archlens.backend.controller;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.dto.GitHubOAuthTokenResponse;
import com.archlens.backend.dto.GitHubRepoDto;
import com.archlens.backend.dto.GitHubUserProfileDto;
import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.repository.GithubCredentialRepository;
import com.archlens.backend.repository.RepoRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.EncryptionService;
import com.archlens.backend.security.JwtService;
import com.archlens.backend.service.GitHubOAuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GithubCredentialRepository credentialRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private GitHubClient gitHubClient;

    @SpyBean
    private GitHubOAuthService oauthService;

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
    void testCreateRepo_PrivateRepo_AnonymousUser_Returns401RequiresAuth() throws Exception {
        // System token fetch gets 404 for private repo
        when(gitHubClient.fetchRepoWithSystemToken(eq("myorg"), eq("secret-repo")))
                .thenThrow(new GitHubClient.GitHubClientException(404, "Not Found"));

        String requestJson = """
                {
                    "url": "https://github.com/myorg/secret-repo"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("requires_github_auth")))
                .andExpect(jsonPath("$.error.message", is("This repository requires GitHub sign-in to access.")))
                .andExpect(jsonPath("$.error.details.loginUrl", is("/auth/github/login?returnRepoUrl=https://github.com/myorg/secret-repo")));
    }

    @Test
    void testCreateRepo_PrivateRepo_AuthenticatedUser_Returns201WithUserOAuth() throws Exception {
        User user = userRepository.save(new User(888999L, "privatedev", "privatedev@example.com", null));
        String rawToken = "gho_user_oauth_secret_token";
        byte[] encryptedToken = encryptionService.encrypt(rawToken);
        credentialRepository.save(new GithubCredential(user, encryptedToken, new String[]{"repo"}, null));

        String sessionJwt = jwtService.generateSessionToken(user);
        Cookie sessionCookie = new Cookie(JwtService.SESSION_COOKIE_NAME, sessionJwt);

        GitHubRepoDto privateRepoDto = new GitHubRepoDto(
                777888L,
                "secret-repo",
                "myorg/secret-repo",
                "main",
                true,
                "https://github.com/myorg/secret-repo",
                "Private organization microservice"
        );

        when(gitHubClient.fetchRepo(eq("myorg"), eq("secret-repo"), eq(rawToken)))
                .thenReturn(privateRepoDto);

        String requestJson = """
                {
                    "url": "https://github.com/myorg/secret-repo"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fullName", is("myorg/secret-repo")))
                .andExpect(jsonPath("$.defaultBranch", is("main")))
                .andExpect(jsonPath("$.accessMethod", is("user_oauth")))
                .andExpect(jsonPath("$.authorizedForTesting", is(false)));

        Optional<Repository> saved = repoRepository.findByFullName("myorg/secret-repo");
        assertTrue(saved.isPresent());
        assertNotNull(saved.get().getUser());
        assertEquals(user.getId(), saved.get().getUser().getId());
        assertEquals("user_oauth", saved.get().getAccessMethod());
    }

    @Test
    void testPrivateRepo_FullRoundTrip_RequiresAuthThenOAuthCallbackThenRetrySucceeds() throws Exception {
        String repoUrl = "https://github.com/roundtrip-org/internal-api";

        // Step 1: Anonymous request gets 401 with requires_github_auth
        when(gitHubClient.fetchRepoWithSystemToken(eq("roundtrip-org"), eq("internal-api")))
                .thenThrow(new GitHubClient.GitHubClientException(404, "Not Found"));

        String requestJson = String.format("{\"url\": \"%s\"}", repoUrl);

        mockMvc.perform(post("/api/v1/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("requires_github_auth")))
                .andExpect(jsonPath("$.error.details.loginUrl", is("/auth/github/login?returnRepoUrl=" + repoUrl)));

        // Step 2: Follow OAuth callback flow
        String encodedState = oauthService.encodeState(repoUrl);
        String userToken = "gho_roundtrip_user_token_999";

        doReturn(new GitHubOAuthTokenResponse(userToken, "bearer", "repo,read:user", null, null))
                .when(oauthService).exchangeCode(anyString());
        doReturn(new GitHubUserProfileDto(555666L, "roundtrip-user", "user@roundtrip.com", null))
                .when(oauthService).fetchUserProfile(anyString());

        MvcResult callbackResult = mockMvc.perform(get("/api/v1/auth/github/callback")
                        .param("code", "auth-code-123")
                        .param("state", encodedState))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(JwtService.SESSION_COOKIE_NAME))
                .andExpect(jsonPath("$.returnRepoUrl", is(repoUrl)))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = callbackResult.getResponse().getCookie(JwtService.SESSION_COOKIE_NAME);
        assertNotNull(sessionCookie);

        // Step 3: Frontend retries POST /repos with the session cookie -> succeeds with user_oauth
        when(gitHubClient.fetchRepo(eq("roundtrip-org"), eq("internal-api"), eq(userToken)))
                .thenReturn(new GitHubRepoDto(
                        333222L,
                        "internal-api",
                        "roundtrip-org/internal-api",
                        "main",
                        true,
                        repoUrl,
                        "Internal API"
                ));

        mockMvc.perform(post("/api/v1/repos")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName", is("roundtrip-org/internal-api")))
                .andExpect(jsonPath("$.accessMethod", is("user_oauth")));

        Optional<Repository> saved = repoRepository.findByFullName("roundtrip-org/internal-api");
        assertTrue(saved.isPresent());
        assertEquals("user_oauth", saved.get().getAccessMethod());
        assertNotNull(saved.get().getUser());
        assertEquals("roundtrip-user", saved.get().getUser().getGithubUsername());
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
    void testCreateRepo_NonExistentRepo_Authenticated_Returns404() throws Exception {
        User user = userRepository.save(new User(444555L, "testauthuser", "auth@example.com", null));
        String rawToken = "gho_test_user_token_valid";
        credentialRepository.save(new GithubCredential(user, encryptionService.encrypt(rawToken), new String[]{"repo"}, null));

        String sessionJwt = jwtService.generateSessionToken(user);
        Cookie sessionCookie = new Cookie(JwtService.SESSION_COOKIE_NAME, sessionJwt);

        when(gitHubClient.fetchRepo(eq("archlens-non-existent-user"), eq("non-existent-repo-12345"), eq(rawToken)))
                .thenThrow(new GitHubClient.GitHubClientException(404, "Repository not found on GitHub"));

        String requestJson = """
                {
                    "url": "https://github.com/archlens-non-existent-user/non-existent-repo-12345"
                }
                """;

        mockMvc.perform(post("/api/v1/repos")
                        .cookie(sessionCookie)
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
