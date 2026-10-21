package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a discovered HTTP endpoint (API definition) linked to a graph node
 * (typically a @RestController service node) in an architecture graph.
 *
 * Populated by the API extraction stage that runs after graph assembly (M2.1).
 */
@Entity
@Table(name = "api_definitions")
public class ApiDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_id", nullable = false)
    private ArchitectureGraph graph;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_node_id", nullable = false)
    private GraphNode graphNode;

    /** HTTP method: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS */
    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    /** Fully resolved URL path, e.g. /api/v1/users/{id} */
    @Column(name = "path", nullable = false, length = 512)
    private String path;

    /** Simple class name of the @RestController (e.g. UserController) */
    @Column(name = "handler_class", length = 255)
    private String handlerClass;

    /** Method name inside the handler class (e.g. getUser) */
    @Column(name = "handler_method", length = 255)
    private String handlerMethod;

    /** Relative path to the source .java file within the repo */
    @Column(name = "source_file", length = 512)
    private String sourceFile;

    /** 1-based line number where the handler method is declared */
    @Column(name = "line_number")
    private Integer lineNumber;

    /** Optional JSONB blob for params, consumes, produces, etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ApiDefinition() {}

    public ApiDefinition(ArchitectureGraph graph, GraphNode graphNode,
                         String httpMethod, String path,
                         String handlerClass, String handlerMethod,
                         String sourceFile, Integer lineNumber, String metadata) {
        this.graph = graph;
        this.graphNode = graphNode;
        this.httpMethod = httpMethod;
        this.path = path;
        this.handlerClass = handlerClass;
        this.handlerMethod = handlerMethod;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ArchitectureGraph getGraph() { return graph; }
    public void setGraph(ArchitectureGraph graph) { this.graph = graph; }

    public GraphNode getGraphNode() { return graphNode; }
    public void setGraphNode(GraphNode graphNode) { this.graphNode = graphNode; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getHandlerClass() { return handlerClass; }
    public void setHandlerClass(String handlerClass) { this.handlerClass = handlerClass; }

    public String getHandlerMethod() { return handlerMethod; }
    public void setHandlerMethod(String handlerMethod) { this.handlerMethod = handlerMethod; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
