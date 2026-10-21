package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_executions")
public class RequestExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_definition_id")
    private ApiDefinition apiDefinition;

    @Column(name = "saved_request_id")
    private UUID savedRequestId;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", columnDefinition = "jsonb")
    private String responseHeaders;

    @Column(name = "response_body_excerpt", columnDefinition = "text")
    private String responseBodyExcerpt;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    public RequestExecution() {}

    public RequestExecution(
            ApiDefinition apiDefinition,
            UUID savedRequestId,
            Integer statusCode,
            Integer latencyMs,
            String responseHeaders,
            String responseBodyExcerpt,
            String error
    ) {
        this.apiDefinition = apiDefinition;
        this.savedRequestId = savedRequestId;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.responseHeaders = responseHeaders;
        this.responseBodyExcerpt = responseBodyExcerpt;
        this.error = error;
        this.executedAt = Instant.now();
    }

    // Getters & Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ApiDefinition getApiDefinition() {
        return apiDefinition;
    }

    public void setApiDefinition(ApiDefinition apiDefinition) {
        this.apiDefinition = apiDefinition;
    }

    public UUID getSavedRequestId() {
        return savedRequestId;
    }

    public void setSavedRequestId(UUID savedRequestId) {
        this.savedRequestId = savedRequestId;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBodyExcerpt() {
        return responseBodyExcerpt;
    }

    public void setResponseBodyExcerpt(String responseBodyExcerpt) {
        this.responseBodyExcerpt = responseBodyExcerpt;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}
