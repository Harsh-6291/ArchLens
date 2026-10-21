package com.archlens.backend.service;

import com.archlens.backend.dto.CreateEnvironmentRequest;
import com.archlens.backend.dto.EnvironmentResponse;
import com.archlens.backend.dto.EnvironmentVariable;
import com.archlens.backend.dto.UpdateEnvironmentRequest;
import com.archlens.backend.entity.Environment;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.EnvironmentRepository;
import com.archlens.backend.security.EncryptionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);
    public static final String MASKED_SECRET = "••••••••";

    private final EnvironmentRepository environmentRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public EnvironmentService(
            EnvironmentRepository environmentRepository,
            EncryptionService encryptionService,
            ObjectMapper objectMapper
    ) {
        this.environmentRepository = environmentRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> getEnvironments(User user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
        }
        return environmentRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentResponse getEnvironment(UUID id, User user) {
        Environment env = findEntity(id, user);
        return toResponse(env);
    }

    /**
     * Resolves all environment variables (decrypting secrets) into a key-value map.
     * Used exclusively by the server-side execution proxy (M2.4).
     */
    @Transactional(readOnly = true)
    public Map<String, String> getResolvedVariables(UUID id, User user) {
        Environment env = findEntity(id, user);
        List<StoredVariable> stored = parseStoredVariables(env.getVariables());

        Map<String, String> resolved = new HashMap<>();
        for (StoredVariable v : stored) {
            if (v.key == null || v.key.isBlank()) continue;
            String val = v.value;
            if (v.secret && val != null) {
                val = encryptionService.decryptFromBase64(val);
            }
            resolved.put(v.key, val != null ? val : "");
        }
        return resolved;
    }

    @Transactional
    public EnvironmentResponse createEnvironment(CreateEnvironmentRequest req, User user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_params", "name is required");
        }

        String name = req.name().trim();
        if (environmentRepository.existsByUserIdAndName(user.getId(), name)) {
            throw new ApiException(HttpStatus.CONFLICT, "environment_already_exists",
                    "An environment with name '" + name + "' already exists");
        }

        List<StoredVariable> stored = processIncomingVariables(req.variables(), Collections.emptyList());
        String json = serializeStoredVariables(stored);

        Environment env = new Environment(user, name, json);
        env = environmentRepository.save(env);
        log.info("Created environment {} ('{}') for user {}", env.getId(), env.getName(), user.getId());

        return toResponse(env);
    }

    @Transactional
    public EnvironmentResponse updateEnvironment(UUID id, UpdateEnvironmentRequest req, User user) {
        Environment env = findEntity(id, user);

        if (req.name() != null && !req.name().isBlank()) {
            String newName = req.name().trim();
            if (!newName.equalsIgnoreCase(env.getName()) && environmentRepository.existsByUserIdAndName(user.getId(), newName)) {
                throw new ApiException(HttpStatus.CONFLICT, "environment_already_exists",
                        "An environment with name '" + newName + "' already exists");
            }
            env.setName(newName);
        }

        if (req.variables() != null) {
            List<StoredVariable> previous = parseStoredVariables(env.getVariables());
            List<StoredVariable> updated = processIncomingVariables(req.variables(), previous);
            env.setVariables(serializeStoredVariables(updated));
        }

        env.setUpdatedAt(Instant.now());
        env = environmentRepository.save(env);
        log.info("Updated environment {} for user {}", env.getId(), user.getId());

        return toResponse(env);
    }

    @Transactional
    public void deleteEnvironment(UUID id, User user) {
        Environment env = findEntity(id, user);
        environmentRepository.delete(env);
        log.info("Deleted environment {} for user {}", id, user.getId());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Environment findEntity(UUID id, User user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
        }
        return environmentRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "environment_not_found", "Environment not found"));
    }

    private List<StoredVariable> processIncomingVariables(List<EnvironmentVariable> incoming, List<StoredVariable> previous) {
        if (incoming == null) return Collections.emptyList();

        Map<String, StoredVariable> prevMap = new HashMap<>();
        for (StoredVariable p : previous) {
            if (p.key != null) prevMap.put(p.key, p);
        }

        List<StoredVariable> result = new ArrayList<>();
        for (EnvironmentVariable iv : incoming) {
            if (iv == null || iv.key() == null || iv.key().isBlank()) continue;

            String key = iv.key().trim();
            String rawVal = iv.value() != null ? iv.value() : "";
            boolean isSecret = iv.secret();

            String storedVal;
            if (isSecret) {
                // If caller sent back masked value and key existed previously as secret, retain previous encrypted value
                if (MASKED_SECRET.equals(rawVal) && prevMap.containsKey(key) && prevMap.get(key).secret) {
                    storedVal = prevMap.get(key).value;
                } else {
                    storedVal = encryptionService.encryptToBase64(rawVal);
                }
            } else {
                storedVal = rawVal;
            }

            result.add(new StoredVariable(key, storedVal, isSecret));
        }
        return result;
    }

    private EnvironmentResponse toResponse(Environment env) {
        List<StoredVariable> stored = parseStoredVariables(env.getVariables());
        List<EnvironmentVariable> vars = new ArrayList<>();

        for (StoredVariable sv : stored) {
            String displayVal = sv.secret ? MASKED_SECRET : sv.value;
            vars.add(new EnvironmentVariable(sv.key, displayVal, sv.secret));
        }

        return new EnvironmentResponse(
                env.getId(),
                env.getUser().getId(),
                env.getName(),
                vars,
                env.getCreatedAt(),
                env.getUpdatedAt()
        );
    }

    private List<StoredVariable> parseStoredVariables(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<StoredVariable>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse stored variables JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String serializeStoredVariables(List<StoredVariable> vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (Exception e) {
            log.error("Failed to serialize stored variables: {}", e.getMessage());
            return "[]";
        }
    }

    public static class StoredVariable {
        public String key;
        public String value;
        public boolean secret;

        public StoredVariable() {}

        public StoredVariable(String key, String value, boolean secret) {
            this.key = key;
            this.value = value;
            this.secret = secret;
        }
    }
}
