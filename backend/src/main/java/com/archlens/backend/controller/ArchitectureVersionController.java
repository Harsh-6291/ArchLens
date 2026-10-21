package com.archlens.backend.controller;

import com.archlens.backend.dto.ArchitectureVersionResponse;
import com.archlens.backend.dto.CreateVersionRequest;
import com.archlens.backend.dto.GraphDiffResponse;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.ArchitectureVersionService;
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
public class ArchitectureVersionController {

    private final ArchitectureVersionService versionService;
    private final UserSessionService userSessionService;

    @Autowired
    public ArchitectureVersionController(
            ArchitectureVersionService versionService,
            UserSessionService userSessionService
    ) {
        this.versionService = versionService;
        this.userSessionService = userSessionService;
    }

    /**
     * POST /graphs/{graphId}/versions
     * Creates a named version snapshot of the current architecture.
     */
    @PostMapping("/versions")
    public ResponseEntity<ArchitectureVersionResponse> createVersion(
            @PathVariable UUID graphId,
            @RequestBody CreateVersionRequest req,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        ArchitectureVersionResponse res = versionService.createVersion(graphId, req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /**
     * GET /graphs/{graphId}/versions
     * Lists all saved version snapshots for this graph.
     */
    @GetMapping("/versions")
    public ResponseEntity<List<ArchitectureVersionResponse>> listVersions(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(versionService.listVersions(graphId, user));
    }

    /**
     * GET /graphs/{graphId}/compare?targetVersionId=...
     * Computes a structural and semantic diff between current graph and a version snapshot.
     */
    @GetMapping("/compare")
    public ResponseEntity<GraphDiffResponse> compareVersion(
            @PathVariable UUID graphId,
            @RequestParam UUID targetVersionId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(versionService.compareVersion(graphId, targetVersionId, user));
    }
}
