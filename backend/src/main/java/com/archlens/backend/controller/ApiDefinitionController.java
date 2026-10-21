package com.archlens.backend.controller;

import com.archlens.backend.dto.*;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.ApiDefinitionService;
import com.archlens.backend.service.ApiExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1", ""})
public class ApiDefinitionController {

    private final ApiDefinitionService apiDefinitionService;
    private final ApiExecutionService apiExecutionService;
    private final UserSessionService userSessionService;

    public ApiDefinitionController(
            ApiDefinitionService apiDefinitionService,
            ApiExecutionService apiExecutionService,
            UserSessionService userSessionService
    ) {
        this.apiDefinitionService = apiDefinitionService;
        this.apiExecutionService = apiExecutionService;
        this.userSessionService = userSessionService;
    }

    /**
     * GET /graphs/{graphId}/api-definitions
     * Lists all API definitions discovered or created for this architecture graph.
     */
    @GetMapping("/graphs/{graphId}/api-definitions")
    public ResponseEntity<ApiDefinitionsListResponse> getDefinitionsForGraph(
            @PathVariable UUID graphId,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(apiDefinitionService.getDefinitionsForGraph(graphId, user));
    }

    /**
     * GET /api-definitions/{id}
     * Full detail of a single API definition.
     */
    @GetMapping("/api-definitions/{id}")
    public ResponseEntity<ApiDefinitionDto> getDefinition(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(apiDefinitionService.getDefinition(id, user));
    }

    /**
     * POST /api-definitions
     * Manually add an API definition to a service node.
     */
    @PostMapping("/api-definitions")
    public ResponseEntity<ApiDefinitionDto> createDefinition(
            @RequestBody CreateApiDefinitionRequest req,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        ApiDefinitionDto created = apiDefinitionService.createDefinition(req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * POST /api-definitions/{id}/execute
     * Proxy-executes the API endpoint using supplied params and environment.
     */
    @PostMapping("/api-definitions/{id}/execute")
    public ResponseEntity<ExecuteApiResponse> execute(
            @PathVariable UUID id,
            @RequestBody(required = false) ExecuteApiRequest req,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        ExecuteApiResponse response = apiExecutionService.execute(id, req, user);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api-definitions/{id}/executions
     * Returns paginated execution history for an API definition.
     */
    @GetMapping("/api-definitions/{id}/executions")
    public ResponseEntity<org.springframework.data.domain.Page<com.archlens.backend.dto.RequestExecutionResponse>> getExecutions(
            @PathVariable UUID id,
            org.springframework.data.domain.Pageable pageable,
            HttpServletRequest request
    ) {
        Optional<User> user = userSessionService.resolveUser(request);
        return ResponseEntity.ok(apiExecutionService.getExecutions(id, pageable, user));
    }
}

