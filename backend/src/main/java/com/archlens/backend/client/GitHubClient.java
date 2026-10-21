package com.archlens.backend.client;

import com.archlens.backend.dto.*;
import com.archlens.backend.entity.SystemGithubCredential;
import com.archlens.backend.repository.SystemGithubCredentialRepository;
import com.archlens.backend.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
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

    public List<BranchDto> fetchBranches(String owner, String repo, String bearerToken) {
        log.info("Fetching branches for {}/{}", owner, repo);

        GitHubBranchResponse[] responses = restClient.get()
                .uri("/repos/{owner}/{repo}/branches", owner, repo)
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("GitHub API error {} fetching branches for {}/{}", response.getStatusCode(), owner, repo);
                    throw new GitHubClientException(response.getStatusCode().value(), "GitHub API error: " + response.getStatusText());
                })
                .body(GitHubBranchResponse[].class);

        if (responses == null) {
            return List.of();
        }

        return Arrays.stream(responses)
                .map(r -> new BranchDto(r.name(), r.commit() != null ? r.commit().sha() : null))
                .toList();
    }

    public List<CommitDto> fetchCommits(String owner, String repo, String branch, String bearerToken) {
        log.info("Fetching commits for {}/{} (branch: {})", owner, repo, branch);

        GitHubCommitResponse[] responses = restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/repos/{owner}/{repo}/commits");
                    if (branch != null && !branch.isBlank()) {
                        builder.queryParam("sha", branch);
                    }
                    return builder.build(owner, repo);
                })
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("GitHub API error {} fetching commits for {}/{}", response.getStatusCode(), owner, repo);
                    throw new GitHubClientException(response.getStatusCode().value(), "GitHub API error: " + response.getStatusText());
                })
                .body(GitHubCommitResponse[].class);

        if (responses == null) {
            return List.of();
        }

        return Arrays.stream(responses)
                .map(c -> new CommitDto(
                        c.sha(),
                        c.commit() != null ? c.commit().message() : null,
                        c.commit() != null && c.commit().author() != null ? c.commit().author().date() : null
                ))
                .toList();
    }

    public byte[] downloadTarball(String owner, String repo, String commitSha, String bearerToken) {
        log.info("Downloading tarball for {}/{} (commit: {})", owner, repo, commitSha);

        return restClient.get()
                .uri("/repos/{owner}/{repo}/tarball/{commitSha}", owner, repo, commitSha)
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("GitHub API error {} downloading tarball for {}/{}", response.getStatusCode(), owner, repo);
                    throw new GitHubClientException(response.getStatusCode().value(), "GitHub API error: " + response.getStatusText());
                })
                .body(byte[].class);
    }

    public record GitHubCommentDto(Long id, String body, String htmlUrl) {}

    public GitHubCommentDto createPrComment(String owner, String repo, int prNumber, String commentBody, String bearerToken) {
        log.info("Posting comment on PR #{}/{} in {}/{}", prNumber, repo, owner, repo);

        return restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issueNumber}/comments", owner, repo, prNumber)
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .body(java.util.Map.of("body", commentBody))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("GitHub API error {} creating comment for PR #{}/{}", response.getStatusCode(), prNumber, repo);
                    throw new GitHubClientException(response.getStatusCode().value(), "GitHub API error: " + response.getStatusText());
                })
                .body(GitHubCommentDto.class);
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
