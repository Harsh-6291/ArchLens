package com.archlens.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_findings")
public class SecurityFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_id", nullable = false)
    private ArchitectureGraph graph;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_node_id")
    private GraphNode graphNode;

    @Column(name = "evidence_id", nullable = false, length = 64)
    private String evidenceId;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW, INFO

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "category", nullable = false, length = 128)
    private String category;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "remediation", nullable = false, columnDefinition = "text")
    private String remediation;

    @Column(name = "source_file", length = 512)
    private String sourceFile;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SecurityFinding() {}

    public SecurityFinding(
            ArchitectureGraph graph,
            GraphNode graphNode,
            String evidenceId,
            String severity,
            String title,
            String category,
            String description,
            String remediation,
            String sourceFile,
            Integer lineNumber
    ) {
        this.graph = graph;
        this.graphNode = graphNode;
        this.evidenceId = evidenceId;
        this.severity = severity;
        this.title = title;
        this.category = category;
        this.description = description;
        this.remediation = remediation;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ArchitectureGraph getGraph() { return graph; }
    public void setGraph(ArchitectureGraph graph) { this.graph = graph; }
    public GraphNode getGraphNode() { return graphNode; }
    public void setGraphNode(GraphNode graphNode) { this.graphNode = graphNode; }
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRemediation() { return remediation; }
    public void setRemediation(String remediation) { this.remediation = remediation; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public Instant getCreatedAt() { return createdAt; }
}
