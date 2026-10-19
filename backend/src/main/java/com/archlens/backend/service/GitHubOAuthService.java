package com.archlens.backend.service;

import com.archlens.backend.dto.AuthCallbackResponseDto;
import com.archlens.backend.dto.GitHubOAuthTokenResponse;
import com.archlens.backend.dto.GitHubUserProfileDto;
import com.archlens.backend.dto.UserDto;
import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.GithubCredentialRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.EncryptionService;
import com.archlens.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class GitHubOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String userUrl;

    private final RestClient restClient;
    private final UserRepository userRepository;
    private final GithubCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final JwtService jwtService;

    public GitHubOAuthService(
            @Value("${archlens.github.oauth.client-id:archlens-mock-client-id}") String clientId,
            @Value("${archlens.github.oauth.client-secret:archlens-mock-client-secret}") String clientSecret,
            @Value("${archlens.github.oauth.redirect-uri:http://localhost:8080/api/v1/auth/github/callback}") String redirectUri,
            @Value("${archlens.github.oauth.authorize-url:https://github.com/login/oauth/authorize}") String authorizeUrl,
            @Value("${archlens.github.oauth.token-url:https://github.com/login/oauth/access_token}") String tokenUrl,
            @Value("${archlens.github.oauth.user-url:https://api.github.com/user}") String userUrl,
            UserRepository userRepository,
            GithubCredentialRepository credentialRepository,
            EncryptionService encryptionService,
            JwtService jwtService
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.userUrl = userUrl;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.jwtService = jwtService;
        this.restClient = RestClient.builder().build();
    }

    public record CallbackProcessResult(
            AuthCallbackResponseDto responseDto,
            String sessionJwt
    ) {}

    public String buildAuthorizeUrl(String returnRepoUrl) {
        String state = encodeState(returnRepoUrl);
        return UriComponentsBuilder.fromHttpUrl(authorizeUrl)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "repo,read:user")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public String encodeState(String returnRepoUrl) {
        if (returnRepoUrl == null || returnRepoUrl.isBlank()) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(returnRepoUrl.getBytes(StandardCharsets.UTF_8));
    }

    public String decodeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(state);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_state", "Invalid OAuth state parameter");
        }
    }

    public GitHubOAuthTokenResponse exchangeCode(String code) {
        try {
            return restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code,
                            "redirect_uri", redirectUri
                    ))
                    .retrieve()
                    .body(GitHubOAuthTokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code for token: ", e);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "github_auth_failed", "Failed to communicate with GitHub OAuth service");
        }
    }

    public GitHubUserProfileDto fetchUserProfile(String accessToken) {
        try {
            return restClient.get()
                    .uri(userUrl)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Accept", "application/vnd.github.v3+json");
                        headers.set("User-Agent", "ArchLens-Auth/1.0");
                    })
                    .retrieve()
                    .body(GitHubUserProfileDto.class);
        } catch (Exception e) {
            log.error("Failed to fetch GitHub user profile: ", e);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "github_auth_failed", "Failed to retrieve GitHub user profile");
        }
    }

    @Transactional
    public CallbackProcessResult handleOAuthCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_state", "Missing authorization code");
        }

        String returnRepoUrl = decodeState(state);

        GitHubOAuthTokenResponse tokenResponse = exchangeCode(code);
        if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
            String desc = tokenResponse != null && tokenResponse.errorDescription() != null
                    ? tokenResponse.errorDescription()
                    : "Invalid authorization code or GitHub OAuth failure";
            throw new ApiException(HttpStatus.UNAUTHORIZED, "github_auth_failed", desc);
        }

        GitHubUserProfileDto profile = fetchUserProfile(tokenResponse.accessToken());
        if (profile == null || profile.id() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "github_auth_failed", "Incomplete GitHub profile received");
        }

        // Upsert User
        User user = userRepository.findByGithubId(profile.id())
                .orElseGet(() -> new User(profile.id(), profile.login(), profile.email(), profile.avatarUrl()));

        user.setGithubUsername(profile.login());
        if (profile.email() != null) {
            user.setEmail(profile.email());
        }
        if (profile.avatarUrl() != null) {
            user.setAvatarUrl(profile.avatarUrl());
        }
        User savedUser = userRepository.save(user);

        // Upsert GithubCredential
        byte[] encryptedToken = encryptionService.encrypt(tokenResponse.accessToken());
        String[] scopes = tokenResponse.scope() != null ? tokenResponse.scope().split(",") : new String[]{"repo", "read:user"};

        GithubCredential credential = credentialRepository.findByUser(savedUser)
                .orElseGet(() -> new GithubCredential(savedUser, encryptedToken, scopes, null));

        credential.setAccessTokenEncrypted(encryptedToken);
        credential.setScopes(scopes);
        credentialRepository.save(credential);

        // Issue session JWT
        String sessionJwt = jwtService.generateSessionToken(savedUser);

        UserDto userDto = new UserDto(savedUser.getId(), savedUser.getGithubUsername(), savedUser.getAvatarUrl());
        AuthCallbackResponseDto responseDto = new AuthCallbackResponseDto(userDto, returnRepoUrl);

        return new CallbackProcessResult(responseDto, sessionJwt);
    }
}
