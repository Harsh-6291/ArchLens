package com.archlens.backend.service;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.repository.AnalysisJobRepository;
import com.archlens.backend.repository.GithubCredentialRepository;
import com.archlens.backend.repository.RepoRepository;
import com.archlens.backend.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * M1.5 Repo Analysis Worker.
 *
 * Fetches repository contents via the GitHub tarball API (not git clone),
 * uses the repo's recorded access method (system token or user OAuth),
 * and extracts the tarball into a temp workspace for downstream analysis.
 */
@Service
public class RepoAnalysisWorkerService {

    private static final Logger log = LoggerFactory.getLogger(RepoAnalysisWorkerService.class);

    private final RepoRepository repoRepository;
    private final AnalysisJobRepository jobRepository;
    private final GitHubClient gitHubClient;
    private final GithubCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final TarballExtractor tarballExtractor;

    @Value("${archlens.worker.workspace-base-dir:${java.io.tmpdir}/archlens-workspaces}")
    private String workspaceBaseDir;

    public RepoAnalysisWorkerService(
            RepoRepository repoRepository,
            AnalysisJobRepository jobRepository,
            GitHubClient gitHubClient,
            GithubCredentialRepository credentialRepository,
            EncryptionService encryptionService,
            TarballExtractor tarballExtractor
    ) {
        this.repoRepository = repoRepository;
        this.jobRepository = jobRepository;
        this.gitHubClient = gitHubClient;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.tarballExtractor = tarballExtractor;
    }

    /**
     * Fetches a repository's contents at the given commitSha into a temp workspace,
     * using whichever credential is on record for the repo.
     *
     * @param repoId    UUID of the repository row
     * @param commitSha commit SHA (or "HEAD") to fetch
     * @return Path to the extracted workspace directory (caller must clean up)
     * @throws IOException if fetching or extraction fails
     */
    public Path fetchRepoContents(UUID repoId, String commitSha) throws IOException {
        Repository repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoId));

        String[] parts = repo.getFullName().split("/", 2);
        String owner = parts[0];
        String repoName = parts.length > 1 ? parts[1] : "";

        String bearerToken = resolveCredential(repo);

        log.info("Fetching tarball for {}/{} at {} using accessMethod={}",
                owner, repoName, commitSha, repo.getAccessMethod());

        byte[] tarball = gitHubClient.downloadTarball(owner, repoName, commitSha, bearerToken);

        Path workspace = createWorkspace(repoId, commitSha);
        tarballExtractor.extractTarGz(tarball, workspace);

        log.info("Repo contents ready at {}", workspace);
        return workspace;
    }

    /**
     * Deletes a workspace directory after analysis is complete.
     */
    public void cleanupWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", path);
                }
            });
            log.debug("Cleaned up workspace: {}", workspace);
        } catch (IOException e) {
            log.warn("Failed to clean up workspace {}: {}", workspace, e.getMessage());
        }
    }

    private String resolveCredential(Repository repo) {
        if ("user_oauth".equals(repo.getAccessMethod()) && repo.getUser() != null) {
            return credentialRepository.findByUser(repo.getUser())
                    .map(GithubCredential::getAccessTokenEncrypted)
                    .map(encryptionService::decrypt)
                    .orElse(null);
        } else {
            return gitHubClient.getDecryptedSystemToken().orElse(null);
        }
    }

    private Path createWorkspace(UUID repoId, String commitSha) throws IOException {
        Path base = Path.of(workspaceBaseDir);
        Files.createDirectories(base);
        String safeSha = commitSha.replaceAll("[^a-zA-Z0-9_-]", "_");
        return base.resolve(repoId + "_" + safeSha);
    }
}
