package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateLoadTestRequest;
import com.archlens.backend.dto.LoadTestResponse;
import com.archlens.backend.dto.LoadTestResultResponse;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.LoadTestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LoadTestController {

    private final LoadTestService loadTestService;
    private final UserSessionService userSessionService;

    public LoadTestController(LoadTestService loadTestService, UserSessionService userSessionService) {
        this.loadTestService = loadTestService;
        this.userSessionService = userSessionService;
    }

    /**
     * POST /api-definitions/{apiDefinitionId}/load-tests
     * Triggers and executes a load test for an API definition.
     */
    @PostMapping("/api-definitions/{apiDefinitionId}/load-tests")
    public ResponseEntity<LoadTestResponse> createAndRun(
            @PathVariable UUID apiDefinitionId,
            @RequestBody CreateLoadTestRequest req,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        LoadTestResponse response = loadTestService.createAndRun(apiDefinitionId, req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api-definitions/{apiDefinitionId}/load-tests
     * Lists all load tests run for an API definition.
     */
    @GetMapping("/api-definitions/{apiDefinitionId}/load-tests")
    public ResponseEntity<List<LoadTestResponse>> list(
            @PathVariable UUID apiDefinitionId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(loadTestService.listForApi(apiDefinitionId, user));
    }

    /**
     * GET /load-tests/{id}
     * Returns load test run status and parameters.
     */
    @GetMapping("/load-tests/{id}")
    public ResponseEntity<LoadTestResponse> get(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(loadTestService.get(id, user));
    }

    /**
     * GET /load-tests/{id}/results
     * Returns full load test results with percentiles, thresholds, and bottleneck diagnosis.
     */
    @GetMapping("/load-tests/{id}/results")
    public ResponseEntity<LoadTestResultResponse> getResult(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(loadTestService.getResult(id, user));
    }
}
