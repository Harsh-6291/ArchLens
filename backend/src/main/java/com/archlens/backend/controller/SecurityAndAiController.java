package com.archlens.backend.controller;

import com.archlens.backend.dto.AiRecommendationResponse;
import com.archlens.backend.dto.SecurityFindingResponse;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.SecurityAndAiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/graphs/{graphId}")
public class SecurityAndAiController {

    private final SecurityAndAiService securityAndAiService;
    private final UserSessionService userSessionService;

    @Autowired
    public SecurityAndAiController(
            SecurityAndAiService securityAndAiService,
            UserSessionService userSessionService
    ) {
        this.securityAndAiService = securityAndAiService;
        this.userSessionService = userSessionService;
    }

    /**
     * POST /graphs/{graphId}/security-scan
     * Runs a static architecture and vulnerability scan.
     */
    @PostMapping("/security-scan")
    public ResponseEntity<List<SecurityFindingResponse>> runSecurityScan(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        List<SecurityFindingResponse> findings = securityAndAiService.runSecurityScan(graphId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(findings);
    }

    /**
     * GET /graphs/{graphId}/security-findings
     * Retrieves existing findings for this graph.
     */
    @GetMapping("/security-findings")
    public ResponseEntity<List<SecurityFindingResponse>> getFindings(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(securityAndAiService.getSecurityFindings(graphId, user));
    }

    /**
     * POST /graphs/{graphId}/ai-recommendations
     * Generates grounded, evidence-verified architectural recommendations.
     */
    @PostMapping("/ai-recommendations")
    public ResponseEntity<List<AiRecommendationResponse>> generateRecommendations(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        List<AiRecommendationResponse> recs = securityAndAiService.generateRecommendations(graphId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(recs);
    }

    /**
     * GET /graphs/{graphId}/ai-recommendations
     * Retrieves existing recommendations for this graph.
     */
    @GetMapping("/ai-recommendations")
    public ResponseEntity<List<AiRecommendationResponse>> getRecommendations(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(securityAndAiService.getRecommendations(graphId, user));
    }
}
