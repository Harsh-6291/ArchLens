package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "architecture_versions")
public class ArchitectureVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_id", nullable = false)
    private ArchitectureGraph graph;

    @Column(name = "version_name", nullable = false)
    private String versionName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data", columnDefinition = "jsonb", nullable = false)
    private String snapshotData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ArchitectureVersion() {}

    public ArchitectureVersion(
            ArchitectureGraph graph,
            String versionName,
            String description,
            String snapshotData
    ) {
        this.graph = graph;
        this.versionName = versionName;
        this.description = description;
        this.snapshotData = snapshotData;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ArchitectureGraph getGraph() { return graph; }
    public void setGraph(ArchitectureGraph graph) { this.graph = graph; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSnapshotData() { return snapshotData; }
    public void setSnapshotData(String snapshotData) { this.snapshotData = snapshotData; }
    public Instant getCreatedAt() { return createdAt; }
}
