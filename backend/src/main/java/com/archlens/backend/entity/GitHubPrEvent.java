package com.archlens.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_pr_events")
public class GitHubPrEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "head_sha", nullable = false, length = 64)
    private String headSha;

    @Column(name = "base_sha", nullable = false, length = 64)
    private String baseSha;

    @Column(name = "head_ref", length = 255)
    private String headRef;

    @Column(name = "base_ref", length = 255)
    private String baseRef;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "received";

    @Column(name = "comment_id", length = 64)
    private String commentId;

    @Column(name = "summary_markdown", columnDefinition = "TEXT")
    private String summaryMarkdown;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public GitHubPrEvent() {}

    public GitHubPrEvent(Repository repository, Integer prNumber, String action, String headSha, String baseSha, String headRef, String baseRef) {
        this.repository = repository;
        this.prNumber = prNumber;
        this.action = action;
        this.headSha = headSha;
        this.baseSha = baseSha;
        this.headRef = headRef;
        this.baseRef = baseRef;
        this.status = "received";
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getBaseSha() {
        return baseSha;
    }

    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public String getHeadRef() {
        return headRef;
    }

    public void setHeadRef(String headRef) {
        this.headRef = headRef;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getSummaryMarkdown() {
        return summaryMarkdown;
    }

    public void setSummaryMarkdown(String summaryMarkdown) {
        this.summaryMarkdown = summaryMarkdown;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
