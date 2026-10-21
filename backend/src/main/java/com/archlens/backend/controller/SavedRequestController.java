package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateSavedRequestRequest;
import com.archlens.backend.dto.SavedRequestResponse;
import com.archlens.backend.dto.UpdateSavedRequestRequest;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.SavedRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoints for saved request CRUD.
 *
 * Base path: /api/v1/api-definitions/{apiDefinitionId}/saved-requests
 */
@RestController
@RequestMapping("/api/v1/api-definitions/{apiDefinitionId}/saved-requests")
public class SavedRequestController {

    private final SavedRequestService savedRequestService;
    private final UserSessionService userSessionService;

    public SavedRequestController(SavedRequestService savedRequestService, UserSessionService userSessionService) {
        this.savedRequestService = savedRequestService;
        this.userSessionService = userSessionService;
    }

    /** GET /api/v1/api-definitions/{apiDefinitionId}/saved-requests */
    @GetMapping
    public List<SavedRequestResponse> list(
            @PathVariable UUID apiDefinitionId,
            HttpServletRequest request) {
        Optional<User> user = userSessionService.resolveUser(request);
        return savedRequestService.list(apiDefinitionId, user);
    }

    /** GET /api/v1/api-definitions/{apiDefinitionId}/saved-requests/{id} */
    @GetMapping("/{id}")
    public SavedRequestResponse get(
            @PathVariable UUID apiDefinitionId,
            @PathVariable UUID id,
            HttpServletRequest request) {
        Optional<User> user = userSessionService.resolveUser(request);
        return savedRequestService.get(apiDefinitionId, id, user);
    }

    /** POST /api/v1/api-definitions/{apiDefinitionId}/saved-requests */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedRequestResponse create(
            @PathVariable UUID apiDefinitionId,
            @RequestBody CreateSavedRequestRequest req,
            HttpServletRequest request) {
        Optional<User> user = userSessionService.resolveUser(request);
        return savedRequestService.create(apiDefinitionId, req, user);
    }

    /** PATCH /api/v1/api-definitions/{apiDefinitionId}/saved-requests/{id} */
    @PatchMapping("/{id}")
    public SavedRequestResponse update(
            @PathVariable UUID apiDefinitionId,
            @PathVariable UUID id,
            @RequestBody UpdateSavedRequestRequest req,
            HttpServletRequest request) {
        Optional<User> user = userSessionService.resolveUser(request);
        return savedRequestService.update(apiDefinitionId, id, req, user);
    }

    /** DELETE /api/v1/api-definitions/{apiDefinitionId}/saved-requests/{id} */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID apiDefinitionId,
            @PathVariable UUID id,
            HttpServletRequest request) {
        Optional<User> user = userSessionService.resolveUser(request);
        savedRequestService.delete(apiDefinitionId, id, user);
    }
}
