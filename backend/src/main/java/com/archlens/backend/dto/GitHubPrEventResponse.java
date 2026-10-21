package com.archlens.backend.dto;

import com.archlens.backend.entity.GitHubPrEvent;
import java.time.Instant;
import java.util.UUID;

public record GitHubPrEventResponse(
        UUID id,
        UUID repositoryId,
        Integer prNumber,
        String action,
        String headSha,
        String baseSha,
        String headRef,
        String baseRef,
        String status,
        String commentId,
        String summaryMarkdown,
        Instant createdAt
) {
    public static GitHubPrEventResponse from(GitHubPrEvent event) {
        return new GitHubPrEventResponse(
                event.getId(),
                event.getRepository() != null ? event.getRepository().getId() : null,
                event.getPrNumber(),
                event.getAction(),
                event.getHeadSha(),
                event.getBaseSha(),
                event.getHeadRef(),
                event.getBaseRef(),
                event.getStatus(),
                event.getCommentId(),
                event.getSummaryMarkdown(),
                event.getCreatedAt()
        );
    }
}
