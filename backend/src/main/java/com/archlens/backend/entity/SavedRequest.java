package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A named, reusable HTTP request template attached to an API definition.
 * Stores path params, query params, headers, body and an optional environment
 * reference so users can replay common test scenarios without re-entering data.
 */
@Entity
@Table(name = "saved_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"api_definition_id", "name"}))
public class SavedRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_definition_id", nullable = false)
    private ApiDefinition apiDefinition;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_params", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> pathParams = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_params", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> queryParams = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> headers = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body", columnDefinition = "jsonb")
    private Object body;

    /** Optional environment whose variables are substituted during execution. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SavedRequest() {}

    public SavedRequest(ApiDefinition apiDefinition, String name, String description,
                        Map<String, String> pathParams, Map<String, String> queryParams,
                        Map<String, String> headers, Object body, Environment environment) {
        this.apiDefinition = apiDefinition;
        this.name = name;
        this.description = description;
        this.pathParams = pathParams != null ? pathParams : Map.of();
        this.queryParams = queryParams != null ? queryParams : Map.of();
        this.headers = headers != null ? headers : Map.of();
        this.body = body;
        this.environment = environment;
    }

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ApiDefinition getApiDefinition() { return apiDefinition; }
    public void setApiDefinition(ApiDefinition apiDefinition) { this.apiDefinition = apiDefinition; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getPathParams() { return pathParams; }
    public void setPathParams(Map<String, String> pathParams) { this.pathParams = pathParams != null ? pathParams : Map.of(); }

    public Map<String, String> getQueryParams() { return queryParams; }
    public void setQueryParams(Map<String, String> queryParams) { this.queryParams = queryParams != null ? queryParams : Map.of(); }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers != null ? headers : Map.of(); }

    public Object getBody() { return body; }
    public void setBody(Object body) { this.body = body; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
