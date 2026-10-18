package com.archlens.backend.client;

import com.archlens.backend.dto.GitHubRepoDto;
import com.archlens.backend.entity.SystemGithubCredential;
import com.archlens.backend.repository.SystemGithubCredentialRepository;
import com.archlens.backend.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private final RestClient restClient;
    private final SystemGithubCredentialRepository systemCredentialRepository;
    private final EncryptionService encryptionService;

    public GitHubClient(
            @Value("${archlens.github.api-base-url:https://api.github.com}") String apiBaseUrl,
            SystemGithubCredentialRepository systemCredentialRepository,
            EncryptionService encryptionService
    ) {
        this.systemCredentialRepository = systemCredentialRepository;
        this.encryptionService = encryptionService;
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "ArchLens-System/1.0")
                .build();
    }

    public Optional<String> getDecryptedSystemToken() {
        return systemCredentialRepository.findActiveCredential()
                .map(SystemGithubCredential::getAccessTokenEncrypted)
                .map(encryptionService::decrypt);
    }

    public GitHubRepoDto fetchRepoWithSystemToken(String owner, String repo) {
        Optional<String> systemToken = getDecryptedSystemToken();
        try {
            return fetchRepo(owner, repo, systemToken.orElse(null));
        } catch (GitHubClientException e) {
            if (e.getStatusCode() == 401 && systemToken.isPresent()) {
                log.warn("System GitHub token was rejected with 401 Unauthorized; attempting unauthenticated fallback for {}/{}", owner, repo);
                return fetchRepo(owner, repo, null);
            }
            throw e;
        }
    }

    public GitHubRepoDto fetchRepo(String owner, String repo, String bearerToken) {
        log.info("Fetching GitHub repository metadata for {}/{}", owner, repo);

        return restClient.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("GitHub API client error {} for {}/{}", response.getStatusCode(), owner, repo);
                    throw new GitHubClientException(response.getStatusCode().value(), "GitHub API error: " + response.getStatusText());
                })
                .body(GitHubRepoDto.class);
    }

    public static class GitHubClientException extends RuntimeException {
        private final int statusCode;

        public GitHubClientException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
