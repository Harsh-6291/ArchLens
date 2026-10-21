package com.archlens.backend.service;

import com.archlens.backend.entity.ApiDefinition;
import com.archlens.backend.entity.ArchitectureGraph;
import com.archlens.backend.entity.GraphNode;
import com.archlens.backend.repository.ApiDefinitionRepository;
import com.archlens.backend.repository.GraphNodeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M2.1 — API Extraction Service.
 *
 * After graph assembly, reads the "routes" list that SpringBootDetector
 * embedded in each SERVICE graph node's metadata JSONB column and persists
 * them as api_definitions rows, one per discovered HTTP handler method.
 *
 * Idempotent: existing api_definitions for the graph are deleted before
 * re-inserting, so re-running analysis for the same repo always produces
 * a fresh, consistent set.
 */
@Service
public class ApiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ApiExtractionService.class);

    private final GraphNodeRepository nodeRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ObjectMapper objectMapper;

    public ApiExtractionService(
            GraphNodeRepository nodeRepository,
            ApiDefinitionRepository apiDefinitionRepository,
            ObjectMapper objectMapper
    ) {
        this.nodeRepository = nodeRepository;
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Extract and persist api_definitions for all SERVICE nodes in the given graph.
     *
     * @param graph the freshly assembled ArchitectureGraph
     */
    @Transactional
    public int extractAndPersist(ArchitectureGraph graph) {
        UUID graphId = graph.getId();
        log.info("Starting API extraction for graphId={}", graphId);

        // Idempotent — wipe any previous definitions for this graph
        apiDefinitionRepository.deleteByGraphId(graphId);

        List<GraphNode> nodes = nodeRepository.findByGraphId(graphId);
        List<ApiDefinition> toSave = new ArrayList<>();

        for (GraphNode node : nodes) {
            if (!"SERVICE".equalsIgnoreCase(node.getType())) continue;

            String metadataJson = node.getMetadata();
            if (metadataJson == null || metadataJson.isBlank()) continue;

            List<Map<String, Object>> routes = extractRoutes(metadataJson);
            if (routes.isEmpty()) continue;

            log.debug("Node '{}' has {} routes to persist", node.getName(), routes.size());

            for (Map<String, Object> route : routes) {
                String httpMethod    = getString(route, "httpMethod", "GET");
                String path          = getString(route, "path", "/");
                String handlerClass  = getString(route, "handlerClass", null);
                String handlerMethod = getString(route, "handlerMethod", null);
                String sourceFile    = getString(route, "sourceFile", node.getSourceFile());
                Integer lineNumber   = getInt(route, "lineNumber");

                ApiDefinition def = new ApiDefinition(
                        graph, node,
                        httpMethod, path,
                        handlerClass, handlerMethod,
                        sourceFile, lineNumber,
                        null   // no extra per-route metadata yet
                );
                toSave.add(def);
            }
        }

        if (!toSave.isEmpty()) {
            apiDefinitionRepository.saveAll(toSave);
            log.info("Persisted {} api_definitions for graphId={}", toSave.size(), graphId);
        } else {
            log.info("No API routes found for graphId={}", graphId);
        }

        return toSave.size();
    }

    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRoutes(String metadataJson) {
        try {
            Map<String, Object> meta = objectMapper.readValue(
                    metadataJson, new TypeReference<Map<String, Object>>() {});
            Object routes = meta.get("routes");
            if (routes instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        } catch (Exception e) {
            log.warn("Failed to parse node metadata JSON for route extraction: {}", e.getMessage());
        }
        return List.of();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return (v instanceof String s) ? s : defaultValue;
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return null;
    }
}
