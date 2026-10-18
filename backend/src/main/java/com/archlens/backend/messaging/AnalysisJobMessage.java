package com.archlens.backend.messaging;

import java.io.Serializable;
import java.util.UUID;

public record AnalysisJobMessage(
        UUID jobId,
        UUID repoId,
        String repoUrl,
        String commitSha,
        String accessMethod,
        boolean failForTesting
) implements Serializable {
    public AnalysisJobMessage(UUID jobId, UUID repoId, String repoUrl, String commitSha, String accessMethod) {
        this(jobId, repoId, repoUrl, commitSha, accessMethod, false);
    }
}
