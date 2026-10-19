package com.archlens.backend.service;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.dto.CreateRepoRequest;
import com.archlens.backend.dto.GitHubRepoDto;
import com.archlens.backend.dto.RepoResponseDto;
import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.GithubCredentialRepository;
import com.archlens.backend.repository.RepoRepository;
import com.archlens.backend.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RepoService {

    private static final Logger log = LoggerFactory.getLogger(RepoService.class);

    // Matches https://github.com/owner/repo or github.com/owner/repo (with optional .git and trailing slash)
    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "^(?:https?:\\/\\/)?(?:www\\.)?github\\.com\\/([a-zA-Z0-9_.-]+)\\/([a-zA-Z0-9_.-]+?)(?:\\.git)?\\/?$"
    );

    private final GitHubClient gitHubClient;
    private final RepoRepository repoRepository;
    private final GithubCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;

    public RepoService(
            GitHubClient gitHubClient,
            RepoRepository repoRepository,
            GithubCredentialRepository credentialRepository,
            EncryptionService encryptionService
    ) {
        this.gitHubClient = gitHubClient;
        this.repoRepository = repoRepository;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
    }

    public record OwnerAndRepo(String owner, String repo) {}

    public OwnerAndRepo parseGitHubUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Invalid GitHub repository URL");
        }

        Matcher matcher = GITHUB_URL_PATTERN.matcher(rawUrl.trim());
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Invalid GitHub repository URL");
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2);

        if (owner.isBlank() || repo.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Invalid GitHub repository URL");
        }

        return new OwnerAndRepo(owner, repo);
    }

    @Transactional
    public RepoResponseDto importPublicRepo(CreateRepoRequest request) {
        return importRepo(request, Optional.empty());
    }

    @Transactional
    public RepoResponseDto importRepo(CreateRepoRequest request, Optional<User> currentUser) {
        OwnerAndRepo ownerAndRepo = parseGitHubUrl(request.url());
        log.info("Processing repository import for {}/{} (authenticated: {})",
                ownerAndRepo.owner(), ownerAndRepo.repo(), currentUser.isPresent());

        // 1. Authenticated user path
        if (currentUser.isPresent()) {
            User user = currentUser.get();
            String userToken = credentialRepository.findByUser(user)
                    .map(GithubCredential::getAccessTokenEncrypted)
                    .map(encryptionService::decrypt)
                    .orElse(null);

            GitHubRepoDto gitHubMetadata;
            try {
                gitHubMetadata = gitHubClient.fetchRepo(ownerAndRepo.owner(), ownerAndRepo.repo(), userToken);
            } catch (GitHubClient.GitHubClientException e) {
                if (e.getStatusCode() == 404) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "repo_not_found", "Repository not found on GitHub");
                } else if (e.getStatusCode() == 429) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "github_rate_limited", "GitHub API rate limit exceeded");
                } else {
                    throw new ApiException(HttpStatus.valueOf(e.getStatusCode()), "github_error", e.getMessage());
                }
            }

            Repository repository = repoRepository.findByFullName(gitHubMetadata.fullName())
                    .orElseGet(() -> new Repository(
                            user,
                            gitHubMetadata.id(),
                            gitHubMetadata.fullName(),
                            gitHubMetadata.defaultBranch(),
                            "user_oauth"
                    ));

            repository.setUser(user);
            repository.setAccessMethod("user_oauth");
            repository.setDefaultBranch(gitHubMetadata.defaultBranch());
            Repository saved = repoRepository.save(repository);

            return new RepoResponseDto(
                    saved.getId(),
                    saved.getFullName(),
                    saved.getDefaultBranch(),
                    saved.getAccessMethod(),
                    saved.isAuthorizedForTesting()
            );
        }

        // 2. Anonymous public-first path
        GitHubRepoDto gitHubMetadata;
        try {
            gitHubMetadata = gitHubClient.fetchRepoWithSystemToken(ownerAndRepo.owner(), ownerAndRepo.repo());
        } catch (GitHubClient.GitHubClientException e) {
            if (e.getStatusCode() == 404 || e.getStatusCode() == 403) {
                // Public fetch failed on potential private repo -> trigger on-demand GitHub sign-in
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "requires_github_auth",
                        "This repository requires GitHub sign-in to access.",
                        Map.of("loginUrl", "/auth/github/login?returnRepoUrl=" + request.url().trim())
                );
            } else if (e.getStatusCode() == 429) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "github_rate_limited", "GitHub API rate limit exceeded");
            } else {
                throw new ApiException(HttpStatus.valueOf(e.getStatusCode()), "github_error", e.getMessage());
            }
        }

        if (gitHubMetadata.isPrivate()) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "requires_github_auth",
                    "This repository requires GitHub sign-in to access.",
                    Map.of("loginUrl", "/auth/github/login?returnRepoUrl=" + request.url().trim())
            );
        }

        Repository repository = repoRepository.findByFullName(gitHubMetadata.fullName())
                .orElseGet(() -> new Repository(
                        null,
                        gitHubMetadata.id(),
                        gitHubMetadata.fullName(),
                        gitHubMetadata.defaultBranch(),
                        "public_system_token"
                ));

        repository.setDefaultBranch(gitHubMetadata.defaultBranch());
        Repository saved = repoRepository.save(repository);

        return new RepoResponseDto(
                saved.getId(),
                saved.getFullName(),
                saved.getDefaultBranch(),
                saved.getAccessMethod(),
                saved.isAuthorizedForTesting()
        );
    }
}
