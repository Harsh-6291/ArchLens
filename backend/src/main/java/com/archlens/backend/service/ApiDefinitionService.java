package com.archlens.backend.service;

import com.archlens.backend.dto.ApiDefinitionDto;
import com.archlens.backend.dto.ApiDefinitionsListResponse;
import com.archlens.backend.dto.CreateApiDefinitionRequest;
import com.archlens.backend.entity.ApiDefinition;
import com.archlens.backend.entity.ArchitectureGraph;
import com.archlens.backend.entity.GraphNode;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.ApiDefinitionRepository;
import com.archlens.backend.repository.ArchitectureGraphRepository;
import com.archlens.backend.repository.GraphNodeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ApiDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ApiDefinitionService.class);

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ArchitectureGraphRepository graphRepository;
    private final GraphNodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    public ApiDefinitionService(
            ApiDefinitionRepository apiDefinitionRepository,
            ArchitectureGraphRepository graphRepository,
            GraphNodeRepository nodeRepository,
            ObjectMapper objectMapper
    ) {
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.graphRepository = graphRepository;
        this.nodeRepository = nodeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ApiDefinitionsListResponse getDefinitionsForGraph(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        List<ApiDefinition> list = apiDefinitionRepository.findByGraphId(graphId);
        List<ApiDefinitionDto> dtos = list.stream()
                .map(this::toDto)
                .toList();

        return new ApiDefinitionsListResponse(dtos);
    }

    @Transactional(readOnly = true)
    public ApiDefinitionDto getDefinition(UUID id, Optional<User> currentUser) {
        ApiDefinition def = apiDefinitionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_definition_not_found", "API definition not found"));

        verifyRepositoryAccess(def.getGraph().getRepository(), currentUser);

        return toDto(def);
    }

    @Transactional
    public ApiDefinitionDto createDefinition(CreateApiDefinitionRequest req, Optional<User> currentUser) {
        if (req.graphNodeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_params", "graphNodeId is required");
        }
        if (req.method() == null || req.method().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_params", "method is required");
        }
        if (req.path() == null || req.path().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_params", "path is required");
        }

        GraphNode node = nodeRepository.findById(req.graphNodeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "node_not_found", "Graph node not found"));

        ArchitectureGraph graph = node.getGraph();
        verifyRepositoryAccess(graph.getRepository(), currentUser);

        String metaJson = null;
        Map<String, Object> metaMap = new HashMap<>();
        if (req.authType() != null && !req.authType().isBlank()) {
            metaMap.put("authType", req.authType());
        }
        if (req.metadata() instanceof Map<?, ?> customMap) {
            for (Map.Entry<?, ?> entry : customMap.entrySet()) {
                metaMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        metaMap.put("discovered", false);

        try {
            metaJson = objectMapper.writeValueAsString(metaMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata for API definition: {}", e.getMessage());
        }

        ApiDefinition def = new ApiDefinition(
                graph,
                node,
                req.method().toUpperCase(Locale.ROOT),
                req.path().trim(),
                null,
                null,
                node.getSourceFile(),
                null,
                metaJson
        );

        def = apiDefinitionRepository.save(def);
        log.info("Created manual API definition {} for node {}", def.getId(), node.getId());
        return toDto(def);
    }

    private ApiDefinitionDto toDto(ApiDefinition def) {
        Object parsedMeta = parseMetadata(def.getMetadata());
        boolean discovered = true;
        if (parsedMeta instanceof Map<?, ?> map && map.containsKey("discovered")) {
            Object discVal = map.get("discovered");
            if (discVal instanceof Boolean b) {
                discovered = b;
            }
        }

        return new ApiDefinitionDto(
                def.getId(),
                def.getGraph().getId(),
                def.getGraphNode().getId(),
                def.getHttpMethod(),
                def.getPath(),
                def.getHandlerClass(),
                def.getHandlerMethod(),
                def.getSourceFile(),
                def.getLineNumber(),
                discovered,
                parsedMeta,
                def.getCreatedAt()
        );
    }

    private Object parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private void verifyRepositoryAccess(Repository repo, Optional<User> currentUser) {
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required to view repository data");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not have permission to view this repository data");
            }
        }
    }
}
