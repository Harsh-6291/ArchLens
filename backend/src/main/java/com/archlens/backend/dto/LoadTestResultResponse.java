package com.archlens.backend.dto;

import com.archlens.backend.entity.LoadTestResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LoadTestResultResponse(
        UUID id,
        UUID loadTestId,
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
        String bottleneckDiagnosis,
        Instant createdAt
) {
    public static LoadTestResultResponse from(LoadTestResult res) {
        return new LoadTestResultResponse(
                res.getId(),
                res.getLoadTest() != null ? res.getLoadTest().getId() : null,
                res.getTotalRequests(),
                res.getSuccessfulRequests(),
                res.getFailedRequests(),
                res.getRequestsPerSecond(),
                res.getP50LatencyMs(),
                res.getP90LatencyMs(),
                res.getP95LatencyMs(),
                res.getP99LatencyMs(),
                res.getMinLatencyMs(),
                res.getMaxLatencyMs(),
                res.getAvgLatencyMs(),
                res.getErrorRate(),
                res.getStatusCodes(),
                res.getCapacityThreshold(),
                res.getBottleneckDiagnosis(),
                res.getCreatedAt()
        );
    }
}
