package com.archlens.backend.controller;

import com.archlens.backend.dto.*;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.GraphService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/graphs", "/graphs"})
public class GraphController {

    private final GraphService graphService;
    private final UserSessionService userSessionService;

    public GraphController(GraphService graphService, UserSessionService userSessionService) {
        this.graphService = graphService;
        this.userSessionService = userSessionService;
    }

    // ─── M1.10 ─────────────────────────────────────────────────────────────────

    @GetMapping("/{graphId}")
    public ResponseEntity<ArchitectureGraphDto> getGraph(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(graphService.getGraph(graphId, user));
    }

    // ─── M1.13 — Manual overlay: nodes ─────────────────────────────────────────

    /**
     * PATCH /graphs/{graphId}/nodes/{nodeId}
     * Partially update a node's name, type, technology, or sourceFile.
     */
    @PatchMapping("/{graphId}/nodes/{nodeId}")
    public ResponseEntity<GraphNodeDto> patchNode(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            @RequestBody PatchNodeRequest request,
            HttpServletRequest servletRequest
    ) {
        Optional<User> user = userSessionService.resolveUser(servletRequest);
        GraphNodeDto updated = graphService.patchNode(graphId, nodeId, request, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /graphs/{graphId}/nodes/{nodeId}
     * Removes a node and all edges incident to it.
     */
    @DeleteMapping("/{graphId}/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @PathVariable UUID graphId,
            @PathVariable UUID nodeId,
            HttpServletRequest servletRequest
    ) {
        Optional<User> user = userSessionService.resolveUser(servletRequest);
        graphService.deleteNode(graphId, nodeId, user);
        return ResponseEntity.noContent().build();
    }

    // ─── M1.13 — Manual overlay: edges ─────────────────────────────────────────

    /**
     * POST /graphs/{graphId}/edges
     * Manually add an edge between two nodes.
     */
    @PostMapping("/{graphId}/edges")
    public ResponseEntity<GraphEdgeDto> createEdge(
            @PathVariable UUID graphId,
            @RequestBody CreateEdgeRequest request,
            HttpServletRequest servletRequest
    ) {
        Optional<User> user = userSessionService.resolveUser(servletRequest);
        GraphEdgeDto edge = graphService.createEdge(graphId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(edge);
    }

    /**
     * DELETE /graphs/{graphId}/edges/{edgeId}
     * Remove an edge.
     */
    @DeleteMapping("/{graphId}/edges/{edgeId}")
    public ResponseEntity<Void> deleteEdge(
            @PathVariable UUID graphId,
            @PathVariable UUID edgeId,
            HttpServletRequest servletRequest
    ) {
        Optional<User> user = userSessionService.resolveUser(servletRequest);
        graphService.deleteEdge(graphId, edgeId, user);
        return ResponseEntity.noContent().build();
    }
}
