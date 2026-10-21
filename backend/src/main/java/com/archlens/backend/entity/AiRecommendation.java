package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_recommendations")
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_id", nullable = false)
    private ArchitectureGraph graph;

    @Column(name = "problem", nullable = false, columnDefinition = "text")
    private String problem;

    @Column(name = "why", nullable = false, columnDefinition = "text")
    private String why;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_ids", columnDefinition = "jsonb", nullable = false)
    private List<String> evidenceIds = List.of();

    @Column(name = "recommendation", nullable = false, columnDefinition = "text")
    private String recommendation;

    @Column(name = "impact", nullable = false, columnDefinition = "text")
    private String impact;

    @Column(name = "considerations", nullable = false, columnDefinition = "text")
    private String considerations;

    @Column(name = "hallucination_verified", nullable = false)
    private boolean hallucinationVerified = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AiRecommendation() {}

    public AiRecommendation(
            ArchitectureGraph graph,
            String problem,
            String why,
            List<String> evidenceIds,
            String recommendation,
            String impact,
            String considerations,
            boolean hallucinationVerified
    ) {
        this.graph = graph;
        this.problem = problem;
        this.why = why;
        this.evidenceIds = evidenceIds != null ? evidenceIds : List.of();
        this.recommendation = recommendation;
        this.impact = impact;
        this.considerations = considerations;
        this.hallucinationVerified = hallucinationVerified;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ArchitectureGraph getGraph() { return graph; }
    public void setGraph(ArchitectureGraph graph) { this.graph = graph; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(List<String> evidenceIds) { this.evidenceIds = evidenceIds; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }
    public String getConsiderations() { return considerations; }
    public void setConsiderations(String considerations) { this.considerations = considerations; }
    public boolean isHallucinationVerified() { return hallucinationVerified; }
    public void setHallucinationVerified(boolean hallucinationVerified) { this.hallucinationVerified = hallucinationVerified; }
    public Instant getCreatedAt() { return createdAt; }
}
