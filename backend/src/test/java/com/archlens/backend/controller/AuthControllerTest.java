package com.archlens.backend.controller;

import com.archlens.backend.dto.GitHubOAuthTokenResponse;
import com.archlens.backend.dto.GitHubUserProfileDto;
import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.User;
import com.archlens.backend.repository.GithubCredentialRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.EncryptionService;
import com.archlens.backend.security.JwtService;
import com.archlens.backend.service.GitHubOAuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GithubCredentialRepository credentialRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JwtService jwtService;

    @SpyBean
    private GitHubOAuthService oauthService;

    @Test
    void testLoginRedirectsToGitHubAuthorizeUrlWithState() throws Exception {
        String returnRepoUrl = "https://github.com/myorg/private-repo";

        mockMvc.perform(get("/api/v1/auth/github/login")
                        .param("returnRepoUrl", returnRepoUrl))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("github.com/login/oauth/authorize")))
                .andExpect(header().string("Location", containsString("client_id=")))
                .andExpect(header().string("Location", containsString("scope=repo,read:user")))
                .andExpect(header().string("Location", containsString("state=")));
    }

    @Test
    void testCallback_MissingCode_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/github/callback")
                        .param("code", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("invalid_state")));
    }

    @Test
    void testCallback_Success_CreatesUserCredentialAndSetsCookie() throws Exception {
        String returnRepoUrl = "https://github.com/myorg/private-repo";
        String encodedState = oauthService.encodeState(returnRepoUrl);

        GitHubOAuthTokenResponse mockToken = new GitHubOAuthTokenResponse(
                "gho_test_user_token_123456",
                "bearer",
                "repo,read:user",
                null,
                null
        );

        GitHubUserProfileDto mockProfile = new GitHubUserProfileDto(
                998877L,
                "private-dev",
                "private-dev@example.com",
                "https://avatars.githubusercontent.com/u/998877"
        );

        doReturn(mockToken).when(oauthService).exchangeCode(anyString());
        doReturn(mockProfile).when(oauthService).fetchUserProfile(anyString());

        mockMvc.perform(get("/api/v1/auth/github/callback")
                        .param("code", "mock-auth-code")
                        .param("state", encodedState))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(JwtService.SESSION_COOKIE_NAME))
                .andExpect(cookie().httpOnly(JwtService.SESSION_COOKIE_NAME, true))
                .andExpect(jsonPath("$.user.githubUsername", is("private-dev")))
                .andExpect(jsonPath("$.user.avatarUrl", is("https://avatars.githubusercontent.com/u/998877")))
                .andExpect(jsonPath("$.returnRepoUrl", is(returnRepoUrl)));

        Optional<User> savedUser = userRepository.findByGithubId(998877L);
        assertTrue(savedUser.isPresent());
        assertEquals("private-dev", savedUser.get().getGithubUsername());

        Optional<GithubCredential> savedCred = credentialRepository.findByUser(savedUser.get());
        assertTrue(savedCred.isPresent());
        assertEquals("gho_test_user_token_123456", encryptionService.decrypt(savedCred.get().getAccessTokenEncrypted()));
    }

    @Test
    void testLogoutClearsSessionCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(JwtService.SESSION_COOKIE_NAME))
                .andExpect(cookie().maxAge(JwtService.SESSION_COOKIE_NAME, 0));
    }
}
