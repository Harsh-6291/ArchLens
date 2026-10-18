package com.archlens.backend.service;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.dto.CreateRepoRequest;
import com.archlens.backend.dto.GitHubRepoDto;
import com.archlens.backend.dto.RepoResponseDto;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public RepoService(GitHubClient gitHubClient, RepoRepository repoRepository) {
        this.gitHubClient = gitHubClient;
        this.repoRepository = repoRepository;
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
        OwnerAndRepo ownerAndRepo = parseGitHubUrl(request.url());
        log.info("Processing public repository import for {}/{}", ownerAndRepo.owner(), ownerAndRepo.repo());

        GitHubRepoDto gitHubMetadata;
        try {
            gitHubMetadata = gitHubClient.fetchRepoWithSystemToken(ownerAndRepo.owner(), ownerAndRepo.repo());
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
