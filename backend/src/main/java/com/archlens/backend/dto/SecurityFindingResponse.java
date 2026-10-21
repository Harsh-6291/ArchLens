package com.archlens.backend.dto;

import com.archlens.backend.entity.SecurityFinding;
import java.time.Instant;
import java.util.UUID;

public record SecurityFindingResponse(
        UUID id,
        UUID graphId,
        UUID graphNodeId,
        String evidenceId,
        String severity,
        String title,
        String category,
        String description,
        String remediation,
        String sourceFile,
        Integer lineNumber,
        Instant createdAt
) {
    public static SecurityFindingResponse from(SecurityFinding sf) {
        return new SecurityFindingResponse(
                sf.getId(),
                sf.getGraph().getId(),
                sf.getGraphNode() != null ? sf.getGraphNode().getId() : null,
                sf.getEvidenceId(),
                sf.getSeverity(),
                sf.getTitle(),
                sf.getCategory(),
                sf.getDescription(),
                sf.getRemediation(),
                sf.getSourceFile(),
                sf.getLineNumber(),
                sf.getCreatedAt()
        );
    }
}
