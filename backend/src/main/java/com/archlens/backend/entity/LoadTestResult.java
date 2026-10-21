package com.archlens.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "load_test_results")
public class LoadTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_test_id", nullable = false, unique = true)
    private LoadTest loadTest;

    @Column(name = "total_requests", nullable = false)
    private Integer totalRequests = 0;

    @Column(name = "successful_requests", nullable = false)
    private Integer successfulRequests = 0;

    @Column(name = "failed_requests", nullable = false)
    private Integer failedRequests = 0;

    @Column(name = "requests_per_second", nullable = false)
    private Double requestsPerSecond = 0.0;

    @Column(name = "p50_latency_ms", nullable = false)
    private Integer p50LatencyMs = 0;

    @Column(name = "p90_latency_ms", nullable = false)
    private Integer p90LatencyMs = 0;

    @Column(name = "p95_latency_ms", nullable = false)
    private Integer p95LatencyMs = 0;

    @Column(name = "p99_latency_ms", nullable = false)
    private Integer p99LatencyMs = 0;

    @Column(name = "min_latency_ms", nullable = false)
    private Integer minLatencyMs = 0;

    @Column(name = "max_latency_ms", nullable = false)
    private Integer maxLatencyMs = 0;

    @Column(name = "avg_latency_ms", nullable = false)
    private Integer avgLatencyMs = 0;

    @Column(name = "error_rate", nullable = false)
    private Double errorRate = 0.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_codes", columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> statusCodes = Map.of();

    @Column(name = "capacity_threshold", nullable = false)
    private String capacityThreshold = "SAFE";

    @Column(name = "bottleneck_diagnosis", columnDefinition = "text")
    private String bottleneckDiagnosis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public LoadTestResult() {}

    public LoadTestResult(
            LoadTest loadTest,
            Integer totalRequests,
            Integer successfulRequests,
            Integer failedRequests,
            Double requestsPerSecond,
            Integer p50LatencyMs,
            Integer p90LatencyMs,
            Integer p95LatencyMs,
            Integer p99LatencyMs,
            Integer minLatencyMs,
            Integer maxLatencyMs,
            Integer avgLatencyMs,
            Double errorRate,
            Map<String, Integer> statusCodes,
            String capacityThreshold,
            String bottleneckDiagnosis
    ) {
        this.loadTest = loadTest;
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.requestsPerSecond = requestsPerSecond;
        this.p50LatencyMs = p50LatencyMs;
        this.p90LatencyMs = p90LatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.avgLatencyMs = avgLatencyMs;
        this.errorRate = errorRate;
        this.statusCodes = statusCodes != null ? statusCodes : Map.of();
        this.capacityThreshold = capacityThreshold != null ? capacityThreshold : "SAFE";
        this.bottleneckDiagnosis = bottleneckDiagnosis;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public LoadTest getLoadTest() { return loadTest; }
    public void setLoadTest(LoadTest loadTest) { this.loadTest = loadTest; }
    public Integer getTotalRequests() { return totalRequests; }
    public void setTotalRequests(Integer totalRequests) { this.totalRequests = totalRequests; }
    public Integer getSuccessfulRequests() { return successfulRequests; }
    public void setSuccessfulRequests(Integer successfulRequests) { this.successfulRequests = successfulRequests; }
    public Integer getFailedRequests() { return failedRequests; }
    public void setFailedRequests(Integer failedRequests) { this.failedRequests = failedRequests; }
    public Double getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(Double requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
    public Integer getP50LatencyMs() { return p50LatencyMs; }
    public void setP50LatencyMs(Integer p50LatencyMs) { this.p50LatencyMs = p50LatencyMs; }
    public Integer getP90LatencyMs() { return p90LatencyMs; }
    public void setP90LatencyMs(Integer p90LatencyMs) { this.p90LatencyMs = p90LatencyMs; }
    public Integer getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(Integer p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }
    public Integer getP99LatencyMs() { return p99LatencyMs; }
    public void setP99LatencyMs(Integer p99LatencyMs) { this.p99LatencyMs = p99LatencyMs; }
    public Integer getMinLatencyMs() { return minLatencyMs; }
    public void setMinLatencyMs(Integer minLatencyMs) { this.minLatencyMs = minLatencyMs; }
    public Integer getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(Integer maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
    public Integer getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(Integer avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public Double getErrorRate() { return errorRate; }
    public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
    public Map<String, Integer> getStatusCodes() { return statusCodes; }
    public void setStatusCodes(Map<String, Integer> statusCodes) { this.statusCodes = statusCodes; }
    public String getCapacityThreshold() { return capacityThreshold; }
    public void setCapacityThreshold(String capacityThreshold) { this.capacityThreshold = capacityThreshold; }
    public String getBottleneckDiagnosis() { return bottleneckDiagnosis; }
    public void setBottleneckDiagnosis(String bottleneckDiagnosis) { this.bottleneckDiagnosis = bottleneckDiagnosis; }
    public Instant getCreatedAt() { return createdAt; }
}
