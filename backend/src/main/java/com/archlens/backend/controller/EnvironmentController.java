package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateEnvironmentRequest;
import com.archlens.backend.dto.EnvironmentResponse;
import com.archlens.backend.dto.UpdateEnvironmentRequest;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.EnvironmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/environments", "/environments"})
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final UserSessionService userSessionService;

    public EnvironmentController(
            EnvironmentService environmentService,
            UserSessionService userSessionService
    ) {
        this.environmentService = environmentService;
        this.userSessionService = userSessionService;
    }

    @GetMapping
    public ResponseEntity<List<EnvironmentResponse>> listEnvironments(HttpServletRequest request) {
        User user = resolveAuthenticatedUser(request);
        return ResponseEntity.ok(environmentService.getEnvironments(user));
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> createEnvironment(
            @RequestBody CreateEnvironmentRequest body,
            HttpServletRequest request
    ) {
        User user = resolveAuthenticatedUser(request);
        EnvironmentResponse created = environmentService.createEnvironment(body, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvironmentResponse> getEnvironment(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        User user = resolveAuthenticatedUser(request);
        return ResponseEntity.ok(environmentService.getEnvironment(id, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnvironmentResponse> updateEnvironment(
            @PathVariable UUID id,
            @RequestBody UpdateEnvironmentRequest body,
            HttpServletRequest request
    ) {
        User user = resolveAuthenticatedUser(request);
        return ResponseEntity.ok(environmentService.updateEnvironment(id, body, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEnvironment(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        User user = resolveAuthenticatedUser(request);
        environmentService.deleteEnvironment(id, user);
        return ResponseEntity.noContent().build();
    }

    private User resolveAuthenticatedUser(HttpServletRequest request) {
        return userSessionService.resolveUser(request)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required"));
    }
}
