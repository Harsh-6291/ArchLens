package com.archlens.backend.service;

import com.archlens.backend.dto.ArchitectureGraphDto;
import com.archlens.backend.dto.CreateEdgeRequest;
import com.archlens.backend.dto.GraphEdgeDto;
import com.archlens.backend.dto.GraphNodeDto;
import com.archlens.backend.dto.PatchNodeRequest;
import com.archlens.backend.entity.ArchitectureGraph;
import com.archlens.backend.entity.GraphEdge;
import com.archlens.backend.entity.GraphNode;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.ArchitectureGraphRepository;
import com.archlens.backend.repository.GraphEdgeRepository;
import com.archlens.backend.repository.GraphNodeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);
    private static final String GRAPH_CACHE_PREFIX = "archlens:graph:";
    private static final String REPO_GRAPHS_SET_PREFIX = "archlens:repo-graphs:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ArchitectureGraphRepository graphRepository;
    private final GraphNodeRepository nodeRepository;
    private final GraphEdgeRepository edgeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GraphService(
            ArchitectureGraphRepository graphRepository,
            GraphNodeRepository nodeRepository,
            GraphEdgeRepository edgeRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.graphRepository = graphRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ArchitectureGraphDto getGraph(UUID graphId, Optional<User> currentUser) {
        String cacheKey = GRAPH_CACHE_PREFIX + graphId;

        // 1. Try reading from Redis cache
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                ArchitectureGraphDto cachedDto = objectMapper.readValue(cachedJson, ArchitectureGraphDto.class);
                // Verify authorization on the cached graph
                verifyAccess(cachedDto.repositoryId(), currentUser);
                log.info("Cache HIT for graph {}", graphId);
                return cachedDto;
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error reading graph {} from Redis cache: {}", graphId, e.getMessage());
        }

        log.info("Cache MISS for graph {}. Querying database...", graphId);

        // 2. Cache miss: query database
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));

        Repository repo = graph.getRepository();
        verifyRepositoryAccess(repo, currentUser);

        List<GraphNode> nodes = nodeRepository.findByGraphId(graphId);
        List<GraphEdge> edges = edgeRepository.findByGraphId(graphId);

        List<GraphNodeDto> nodeDtos = nodes.stream()
                .map(this::toNodeDto)
                .toList();

        List<GraphEdgeDto> edgeDtos = edges.stream()
                .map(this::toEdgeDto)
                .toList();

        ArchitectureGraphDto dto = new ArchitectureGraphDto(
                graph.getId(),
                graph.getAnalysisJob().getId(),
                repo.getId(),
                graph.getCommitSha(),
                nodeDtos,
                edgeDtos
        );

        // 3. Store in Redis cache and track repo-graph association for invalidation
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            redisTemplate.opsForSet().add(REPO_GRAPHS_SET_PREFIX + repo.getId(), graphId.toString());
            log.info("Cached graph {} in Redis with TTL={}", graphId, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to write graph {} to Redis cache: {}", graphId, e.getMessage());
        }

        return dto;
    }

    /**
     * Invalidates all cached graphs associated with a repository.
     */
    public void evictGraphsForRepo(UUID repoId) {
        if (repoId == null) return;
        String setKey = REPO_GRAPHS_SET_PREFIX + repoId;
        try {
            Set<String> graphIds = redisTemplate.opsForSet().members(setKey);
            if (graphIds != null && !graphIds.isEmpty()) {
                List<String> keysToDelete = new ArrayList<>();
                for (String gId : graphIds) {
                    keysToDelete.add(GRAPH_CACHE_PREFIX + gId);
                }
                redisTemplate.delete(keysToDelete);
                redisTemplate.delete(setKey);
                log.info("Invalidated {} cached graphs for repo {}", keysToDelete.size(), repoId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate cache for repo {}: {}", repoId, e.getMessage());
        }
    }

    private void verifyAccess(UUID repoId, Optional<User> currentUser) {
        // Look up repo access method to enforce ownership
        graphRepository.findAll().stream()
                .filter(g -> g.getRepository().getId().equals(repoId))
                .findFirst()
                .ifPresent(g -> verifyRepositoryAccess(g.getRepository(), currentUser));
    }

    private void verifyRepositoryAccess(Repository repo, Optional<User> currentUser) {
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required to view private repository graph");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not have permission to view this graph");
            }
        }
    }

    private GraphNodeDto toNodeDto(GraphNode node) {
        Object meta = parseMetadata(node.getMetadata());
        return new GraphNodeDto(
                node.getId(),
                node.getNodeKey(),
                node.getName(),
                node.getType(),
                node.getTechnology(),
                node.getConfidence(),
                node.getSourceFile(),
                meta
        );
    }

    private GraphEdgeDto toEdgeDto(GraphEdge edge) {
        Object meta = parseMetadata(edge.getMetadata());
        return new GraphEdgeDto(
                edge.getId(),
                edge.getSourceNode().getId(),
                edge.getTargetNode().getId(),
                edge.getEdgeType(),
                edge.getConfidence(),
                meta
        );
    }

    private Object parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    // -------------------------------------------------------------------------
    // M1.13 — Manual graph overlay: patch node, delete node, create edge, delete edge
    // -------------------------------------------------------------------------

    /**
     * Partially update a graph node's editable fields (name, type, technology, sourceFile).
     * Evicts the graph from Redis cache so subsequent GET returns fresh data.
     */
    @Transactional
    public GraphNodeDto patchNode(UUID graphId, UUID nodeId, PatchNodeRequest req, Optional<User> currentUser) {
        ArchitectureGraph graph = resolveGraphWithAccess(graphId, currentUser);

        GraphNode node = nodeRepository.findById(nodeId)
                .filter(n -> n.getGraph().getId().equals(graphId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "node_not_found", "Node not found in this graph"));

        if (req.name()       != null) node.setName(req.name());
        if (req.type()       != null) node.setType(req.type());
        if (req.technology() != null) node.setTechnology(req.technology());
        if (req.sourceFile() != null) node.setSourceFile(req.sourceFile());

        node = nodeRepository.save(node);
        evictGraphCache(graphId, graph.getRepository().getId());
        log.info("Patched node {} in graph {}", nodeId, graphId);
        return toNodeDto(node);
    }

    /**
     * Delete a node and all edges referencing it.
     * Evicts the graph from Redis cache.
     */
    @Transactional
    public void deleteNode(UUID graphId, UUID nodeId, Optional<User> currentUser) {
        ArchitectureGraph graph = resolveGraphWithAccess(graphId, currentUser);

        GraphNode node = nodeRepository.findById(nodeId)
                .filter(n -> n.getGraph().getId().equals(graphId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "node_not_found", "Node not found in this graph"));

        // Delete edges that reference this node (source or target)
        List<GraphEdge> edges = edgeRepository.findByGraphId(graphId);
        List<GraphEdge> toRemove = edges.stream()
                .filter(e -> e.getSourceNode().getId().equals(nodeId) || e.getTargetNode().getId().equals(nodeId))
                .toList();
        edgeRepository.deleteAll(toRemove);
        nodeRepository.delete(node);

        evictGraphCache(graphId, graph.getRepository().getId());
        log.info("Deleted node {} (and {} incident edges) from graph {}", nodeId, toRemove.size(), graphId);
    }

    /**
     * Manually create an edge between two nodes in the same graph.
     * Evicts the graph from Redis cache.
     */
    @Transactional
    public GraphEdgeDto createEdge(UUID graphId, CreateEdgeRequest req, Optional<User> currentUser) {
        ArchitectureGraph graph = resolveGraphWithAccess(graphId, currentUser);

        GraphNode src = nodeRepository.findById(req.sourceNodeId())
                .filter(n -> n.getGraph().getId().equals(graphId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "source_node_not_found", "Source node not found in this graph"));

        GraphNode tgt = nodeRepository.findById(req.targetNodeId())
                .filter(n -> n.getGraph().getId().equals(graphId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "target_node_not_found", "Target node not found in this graph"));

        String edgeType = (req.edgeType() != null && !req.edgeType().isBlank()) ? req.edgeType().toUpperCase() : "CALLS";
        GraphEdge edge = new GraphEdge(graph, src, tgt, edgeType, 1.0, "{\"manual\":true}");
        edge = edgeRepository.save(edge);

        evictGraphCache(graphId, graph.getRepository().getId());
        log.info("Created manual edge {} ({} → {}) in graph {}", edge.getId(), src.getName(), tgt.getName(), graphId);
        return toEdgeDto(edge);
    }

    /**
     * Delete an edge from a graph.
     * Evicts the graph from Redis cache.
     */
    @Transactional
    public void deleteEdge(UUID graphId, UUID edgeId, Optional<User> currentUser) {
        ArchitectureGraph graph = resolveGraphWithAccess(graphId, currentUser);

        GraphEdge edge = edgeRepository.findById(edgeId)
                .filter(e -> e.getGraph().getId().equals(graphId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "edge_not_found", "Edge not found in this graph"));

        edgeRepository.delete(edge);
        evictGraphCache(graphId, graph.getRepository().getId());
        log.info("Deleted edge {} from graph {}", edgeId, graphId);
    }

    // -------------------------------------------------------------------------

    private ArchitectureGraph resolveGraphWithAccess(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));
        verifyRepositoryAccess(graph.getRepository(), currentUser);
        return graph;
    }

    private void evictGraphCache(UUID graphId, UUID repoId) {
        try {
            redisTemplate.delete(GRAPH_CACHE_PREFIX + graphId);
            redisTemplate.opsForSet().remove(REPO_GRAPHS_SET_PREFIX + repoId, graphId.toString());
        } catch (Exception e) {
            log.warn("Failed to evict graph {} from cache: {}", graphId, e.getMessage());
        }
    }
}
