package com.archlens.backend.dto;

import com.archlens.backend.entity.AiRecommendation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiRecommendationResponse(
        UUID id,
        UUID graphId,
        String problem,
        String why,
        List<String> evidenceIds,
        String recommendation,
        String impact,
        String considerations,
        boolean hallucinationVerified,
        Instant createdAt
) {
    public static AiRecommendationResponse from(AiRecommendation ar) {
        return new AiRecommendationResponse(
                ar.getId(),
                ar.getGraph().getId(),
                ar.getProblem(),
                ar.getWhy(),
                ar.getEvidenceIds(),
                ar.getRecommendation(),
                ar.getImpact(),
                ar.getConsiderations(),
                ar.isHallucinationVerified(),
                ar.getCreatedAt()
        );
    }
}
