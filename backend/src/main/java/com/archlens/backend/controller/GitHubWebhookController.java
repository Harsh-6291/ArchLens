package com.archlens.backend.controller;

import com.archlens.backend.dto.GitHubPrEventResponse;
import com.archlens.backend.dto.GitHubPullRequestPayload;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.GitHubPrEventRepository;
import com.archlens.backend.service.GitHubPrWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubPrWebhookService webhookService;
    private final GitHubPrEventRepository prEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public GitHubWebhookController(
            GitHubPrWebhookService webhookService,
            GitHubPrEventRepository prEventRepository,
            ObjectMapper objectMapper
    ) {
        this.webhookService = webhookService;
        this.prEventRepository = prEventRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks/github/pr")
    public ResponseEntity<GitHubPrEventResponse> handlePullRequestWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "pull_request") String eventType,
            @RequestBody String rawBody
    ) {
        log.info("Received GitHub webhook event: {}", eventType);

        if (!webhookService.verifySignature(rawBody, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_signature", "GitHub webhook signature mismatch");
        }

        if (!"pull_request".equalsIgnoreCase(eventType)) {
            log.info("Ignoring non-PR GitHub event: {}", eventType);
            return ResponseEntity.ok(null);
        }

        GitHubPullRequestPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, GitHubPullRequestPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook payload: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_payload", "Cannot parse PR payload: " + e.getMessage());
        }

        GitHubPrEventResponse response = webhookService.processPullRequestEvent(payload);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/repos/{repoId}/pr-events")
    public ResponseEntity<List<GitHubPrEventResponse>> getRepoPrEvents(@PathVariable UUID repoId) {
        List<GitHubPrEventResponse> events = prEventRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId)
                .stream()
                .map(GitHubPrEventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }
}
