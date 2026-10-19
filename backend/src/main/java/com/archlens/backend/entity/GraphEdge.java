package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graph_edges")
public class GraphEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_id", nullable = false)
    private ArchitectureGraph graph;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_node_id", nullable = false)
    private GraphNode sourceNode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_node_id", nullable = false)
    private GraphNode targetNode;

    @Column(name = "edge_type", nullable = false)
    private String edgeType;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public GraphEdge() {}

    public GraphEdge(ArchitectureGraph graph, GraphNode sourceNode, GraphNode targetNode,
                     String edgeType, Double confidence, String metadata) {
        this.graph = graph;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.edgeType = edgeType;
        this.confidence = confidence;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ArchitectureGraph getGraph() {
        return graph;
    }

    public void setGraph(ArchitectureGraph graph) {
        this.graph = graph;
    }

    public GraphNode getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(GraphNode sourceNode) {
        this.sourceNode = sourceNode;
    }

    public GraphNode getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(GraphNode targetNode) {
        this.targetNode = targetNode;
    }

    public String getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
