package com.archlens.backend.service;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.dto.AnalysisJobStatusResponseDto;
import com.archlens.backend.dto.CommitDto;
import com.archlens.backend.dto.CreateAnalysisJobRequest;
import com.archlens.backend.dto.CreateAnalysisJobResponseDto;
import com.archlens.backend.entity.AnalysisJob;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.messaging.AnalysisJobMessage;
import com.archlens.backend.messaging.AnalysisJobProducer;
import com.archlens.backend.repository.AnalysisJobRepository;
import com.archlens.backend.repository.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisJobService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);

    private final AnalysisJobRepository jobRepository;
    private final RepoRepository repoRepository;
    private final RepoService repoService;
    private final AnalysisJobProducer jobProducer;

    public AnalysisJobService(
            AnalysisJobRepository jobRepository,
            RepoRepository repoRepository,
            RepoService repoService,
            AnalysisJobProducer jobProducer
    ) {
        this.jobRepository = jobRepository;
        this.repoRepository = repoRepository;
        this.repoService = repoService;
        this.jobProducer = jobProducer;
    }

    @Transactional
    public CreateAnalysisJobResponseDto createJob(CreateAnalysisJobRequest request, Optional<User> currentUser) {
        if (request == null || request.repositoryId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "repositoryId must not be null");
        }

        Repository repo = repoRepository.findById(request.repositoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "repo_not_found", "Repository not found"));

        // Check ownership if private repository
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required for private repository analysis");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not own this repository");
            }
        }

        String branch = (request.branch() != null && !request.branch().isBlank())
                ? request.branch().trim()
                : repo.getDefaultBranch();

        String commitSha = (request.commitSha() != null && !request.commitSha().isBlank())
                ? request.commitSha().trim()
                : resolveLatestCommitSha(repo.getId(), branch, currentUser);

        // Check if identical analysis is already in flight
        Optional<AnalysisJob> inFlight = jobRepository.findInFlightJobForCommit(repo.getId(), commitSha);
        if (inFlight.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "analysis_already_running", "An analysis job is already in flight for this commit");
        }

        AnalysisJob job = new AnalysisJob(repo, branch, commitSha);
        job = jobRepository.save(job);

        AnalysisJobMessage message = new AnalysisJobMessage(
                job.getId(),
                repo.getId(),
                "https://github.com/" + repo.getFullName(),
                commitSha,
                repo.getAccessMethod()
        );
        jobProducer.sendAnalysisJob(message);

        log.info("Dispatched analysis job {} for repo {} ({})", job.getId(), repo.getFullName(), commitSha);
        return new CreateAnalysisJobResponseDto(job.getId(), job.getStatus());
    }

    @Transactional(readOnly = true)
    public AnalysisJobStatusResponseDto getJobStatus(UUID jobId, Optional<User> currentUser) {
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Analysis job not found"));

        Repository repo = job.getRepository();
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty() || (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId()))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not have permission to view this job");
            }
        }

        return new AnalysisJobStatusResponseDto(
                job.getId(),
                job.getStatus(),
                new AnalysisJobStatusResponseDto.Progress(job.getStage(), job.getPercent()),
                job.getGraphId(),
                job.getErrorMessage()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobProgress(UUID jobId, String status, String stage, Integer percent, String errorMessage) {
        updateJobProgressAndResults(jobId, status, stage, percent, null, errorMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobProgressAndResults(UUID jobId, String status, String stage, Integer percent, String detectorResults, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (status != null) {
                job.setStatus(status);
            }
            if (stage != null) {
                job.setStage(stage);
            }
            if (percent != null) {
                job.setPercent(percent);
            }
            if (detectorResults != null) {
                job.setDetectorResults(detectorResults);
            }
            if ("running".equals(status) && job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
            }
            if ("succeeded".equals(status) || "failed".equals(status)) {
                job.setFinishedAt(Instant.now());
            }
            if (errorMessage != null) {
                job.setErrorMessage(errorMessage);
            }
            jobRepository.save(job);
        });
    }

    private String resolveLatestCommitSha(UUID repoId, String branch, Optional<User> currentUser) {
        try {
            var commitsDto = repoService.listCommits(repoId, branch, currentUser);
            if (commitsDto != null && commitsDto.commits() != null && !commitsDto.commits().isEmpty()) {
                return commitsDto.commits().get(0).sha();
            }
        } catch (Exception e) {
            log.warn("Could not fetch remote commits for repo {} branch {}: {}", repoId, branch, e.getMessage());
        }
        return "HEAD";
    }
}
