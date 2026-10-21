package com.archlens.backend.service;

import com.archlens.backend.dto.CreateSavedRequestRequest;
import com.archlens.backend.dto.SavedRequestResponse;
import com.archlens.backend.dto.UpdateSavedRequestRequest;
import com.archlens.backend.entity.ApiDefinition;
import com.archlens.backend.entity.Environment;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.SavedRequest;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.ApiDefinitionRepository;
import com.archlens.backend.repository.EnvironmentRepository;
import com.archlens.backend.repository.SavedRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SavedRequestService {

    private final SavedRequestRepository savedRequestRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final EnvironmentRepository environmentRepository;

    public SavedRequestService(SavedRequestRepository savedRequestRepository,
                               ApiDefinitionRepository apiDefinitionRepository,
                               EnvironmentRepository environmentRepository) {
        this.savedRequestRepository = savedRequestRepository;
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.environmentRepository = environmentRepository;
    }

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public List<SavedRequestResponse> list(UUID apiDefinitionId, Optional<User> currentUser) {
        ApiDefinition apiDef = loadApiDefWithAccess(apiDefinitionId, currentUser);
        return savedRequestRepository
                .findByApiDefinitionIdOrderByCreatedAtAsc(apiDef.getId())
                .stream()
                .map(SavedRequestResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------ get

    @Transactional(readOnly = true)
    public SavedRequestResponse get(UUID apiDefinitionId, UUID savedRequestId, Optional<User> currentUser) {
        ApiDefinition apiDef = loadApiDefWithAccess(apiDefinitionId, currentUser);
        SavedRequest sr = loadSavedRequest(savedRequestId, apiDef.getId());
        return SavedRequestResponse.from(sr);
    }

    // ----------------------------------------------------------------- create

    @Transactional
    public SavedRequestResponse create(UUID apiDefinitionId, CreateSavedRequestRequest req, Optional<User> currentUser) {
        ApiDefinition apiDef = loadApiDefWithAccess(apiDefinitionId, currentUser);

        // Validate name
        if (req.name() == null || req.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "'name' must not be blank");
        }
        if (req.name().length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "'name' must be 255 characters or fewer");
        }

        if (savedRequestRepository.findByApiDefinitionIdAndName(apiDef.getId(), req.name()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "saved_request_name_conflict",
                    "A saved request named '" + req.name() + "' already exists for this API definition");
        }

        Environment env = resolveEnvironment(req.environmentId(), currentUser);

        SavedRequest sr = new SavedRequest(
                apiDef,
                req.name(),
                req.description(),
                req.pathParams(),
                req.queryParams(),
                req.headers(),
                req.body(),
                env
        );

        return SavedRequestResponse.from(savedRequestRepository.save(sr));
    }

    // ----------------------------------------------------------------- update

    @Transactional
    public SavedRequestResponse update(UUID apiDefinitionId, UUID savedRequestId,
                                       UpdateSavedRequestRequest req, Optional<User> currentUser) {
        ApiDefinition apiDef = loadApiDefWithAccess(apiDefinitionId, currentUser);
        SavedRequest sr = loadSavedRequest(savedRequestId, apiDef.getId());

        if (req.name() != null && !req.name().equals(sr.getName())) {
            if (savedRequestRepository.existsByApiDefinitionIdAndNameAndIdNot(apiDef.getId(), req.name(), sr.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "saved_request_name_conflict",
                        "A saved request named '" + req.name() + "' already exists for this API definition");
            }
            sr.setName(req.name());
        }
        if (req.description() != null) sr.setDescription(req.description());
        if (req.pathParams() != null)  sr.setPathParams(req.pathParams());
        if (req.queryParams() != null) sr.setQueryParams(req.queryParams());
        if (req.headers() != null)     sr.setHeaders(req.headers());
        if (req.body() != null)        sr.setBody(req.body());

        if (req.environmentId() != null) {
            sr.setEnvironment(resolveEnvironment(req.environmentId(), currentUser));
        }

        return SavedRequestResponse.from(savedRequestRepository.save(sr));
    }

    // ----------------------------------------------------------------- delete

    @Transactional
    public void delete(UUID apiDefinitionId, UUID savedRequestId, Optional<User> currentUser) {
        ApiDefinition apiDef = loadApiDefWithAccess(apiDefinitionId, currentUser);
        SavedRequest sr = loadSavedRequest(savedRequestId, apiDef.getId());
        savedRequestRepository.delete(sr);
    }

    // ----------------------------------------------------------- private helpers

    private ApiDefinition loadApiDefWithAccess(UUID apiDefinitionId, Optional<User> currentUser) {
        ApiDefinition apiDef = apiDefinitionRepository.findWithGraphById(apiDefinitionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_definition_not_found",
                        "API definition not found"));
        verifyRepositoryAccess(apiDef.getGraph().getRepository(), currentUser);
        return apiDef;
    }

    private SavedRequest loadSavedRequest(UUID savedRequestId, UUID apiDefinitionId) {
        SavedRequest sr = savedRequestRepository.findById(savedRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "saved_request_not_found",
                        "Saved request not found"));
        if (!sr.getApiDefinition().getId().equals(apiDefinitionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "saved_request_not_found",
                    "Saved request does not belong to the specified API definition");
        }
        return sr;
    }

    private Environment resolveEnvironment(UUID environmentId, Optional<User> currentUser) {
        if (environmentId == null) return null;
        return environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "environment_not_found",
                        "Environment not found: " + environmentId));
    }

    private void verifyRepositoryAccess(Repository repo, Optional<User> currentUser) {
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized",
                        "Authentication required to access this resource");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner",
                        "You do not have permission to access this resource");
            }
        }
    }
}
