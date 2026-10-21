package com.archlens.backend.service;

import com.archlens.backend.dto.*;
import com.archlens.backend.entity.*;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.ArchitectureGraphRepository;
import com.archlens.backend.repository.ArchitectureVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArchitectureVersionService {

    private final ArchitectureVersionRepository versionRepository;
    private final ArchitectureGraphRepository graphRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArchitectureVersionService(
            ArchitectureVersionRepository versionRepository,
            ArchitectureGraphRepository graphRepository,
            ObjectMapper objectMapper
    ) {
        this.versionRepository = versionRepository;
        this.graphRepository = graphRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------- Create Version

    @Transactional
    public ArchitectureVersionResponse createVersion(UUID graphId, CreateVersionRequest req, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        if (req == null || req.versionName() == null || req.versionName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "'versionName' must not be blank");
        }

        // Serialize current graph state as snapshot
        ArchitectureGraphDto snapshotDto = toGraphDto(graph);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshotDto);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "serialization_error", "Failed to serialize snapshot");
        }

        ArchitectureVersion version = new ArchitectureVersion(
                graph,
                req.versionName(),
                req.description(),
                snapshotJson
        );
        version = versionRepository.save(version);
        return ArchitectureVersionResponse.from(version);
    }

    // ---------------------------------------------------------------- List

    @Transactional(readOnly = true)
    public List<ArchitectureVersionResponse> listVersions(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        return versionRepository.findByGraphIdOrderByCreatedAtDesc(graph.getId())
                .stream()
                .map(ArchitectureVersionResponse::from)
                .toList();
    }

    // ---------------------------------------------------------------- Diff Engine

    @Transactional(readOnly = true)
    public GraphDiffResponse compareVersion(UUID graphId, UUID versionId, Optional<User> currentUser) {
        ArchitectureGraph currentGraph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Graph not found"));

        verifyRepositoryAccess(currentGraph.getRepository(), currentUser);

        ArchitectureVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "version_not_found", "Version not found"));

        ArchitectureGraphDto snapshot;
        try {
            snapshot = objectMapper.readValue(version.getSnapshotData(), ArchitectureGraphDto.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "deserialization_error", "Failed to deserialize snapshot");
        }

        ArchitectureGraphDto currentDto = toGraphDto(currentGraph);

        // Match nodes by node_key
        Map<String, GraphNodeDto> snapshotNodeMap = snapshot.nodes().stream()
                .collect(Collectors.toMap(GraphNodeDto::nodeKey, Function.identity(), (a, b) -> a));

        Map<String, GraphNodeDto> currentNodeMap = currentDto.nodes().stream()
                .collect(Collectors.toMap(GraphNodeDto::nodeKey, Function.identity(), (a, b) -> a));

        List<GraphNodeDto> addedNodes = new ArrayList<>();
        List<GraphNodeDto> removedNodes = new ArrayList<>();
        List<GraphDiffResponse.ModifiedNodeDiff> modifiedNodes = new ArrayList<>();

        // Detect added or modified
        for (Map.Entry<String, GraphNodeDto> entry : currentNodeMap.entrySet()) {
            String key = entry.getKey();
            GraphNodeDto current = entry.getValue();
            if (!snapshotNodeMap.containsKey(key)) {
                addedNodes.add(current);
            } else {
                GraphNodeDto old = snapshotNodeMap.get(key);
                boolean changed = !Objects.equals(old.name(), current.name())
                        || !Objects.equals(old.type(), current.type())
                        || !Objects.equals(old.technology(), current.technology());
                if (changed) {
                    modifiedNodes.add(new GraphDiffResponse.ModifiedNodeDiff(
                            key,
                            old.name(), current.name(),
                            old.type(), current.type(),
                            old.technology(), current.technology()
                    ));
                }
            }
        }

        // Detect removed
        for (Map.Entry<String, GraphNodeDto> entry : snapshotNodeMap.entrySet()) {
            if (!currentNodeMap.containsKey(entry.getKey())) {
                removedNodes.add(entry.getValue());
            }
        }

        // Compare edges
        Set<String> snapshotEdgeSignatures = snapshot.edges().stream()
                .map(e -> e.source() + "->" + e.target() + ":" + e.type())
                .collect(Collectors.toSet());

        Set<String> currentEdgeSignatures = currentDto.edges().stream()
                .map(e -> e.source() + "->" + e.target() + ":" + e.type())
                .collect(Collectors.toSet());

        List<GraphEdgeDto> addedEdges = currentDto.edges().stream()
                .filter(e -> !snapshotEdgeSignatures.contains(e.source() + "->" + e.target() + ":" + e.type()))
                .toList();

        List<GraphEdgeDto> removedEdges = snapshot.edges().stream()
                .filter(e -> !currentEdgeSignatures.contains(e.source() + "->" + e.target() + ":" + e.type()))
                .toList();

        return new GraphDiffResponse(
                currentGraph.getId(),
                version.getId(),
                version.getVersionName(),
                addedNodes,
                removedNodes,
                modifiedNodes,
                addedEdges,
                removedEdges
        );
    }

    private ArchitectureGraphDto toGraphDto(ArchitectureGraph graph) {
        List<GraphNodeDto> nodeDtos = graph.getNodes().stream()
                .map(n -> new GraphNodeDto(n.getId(), n.getNodeKey(), n.getName(), n.getType(), n.getTechnology(), n.getConfidence(), n.getSourceFile(), n.getMetadata()))
                .toList();

        List<GraphEdgeDto> edgeDtos = graph.getEdges().stream()
                .map(e -> new GraphEdgeDto(e.getId(), e.getSourceNode().getId(), e.getTargetNode().getId(), e.getEdgeType(), e.getConfidence(), e.getMetadata()))
                .toList();

        return new ArchitectureGraphDto(
                graph.getId(),
                graph.getAnalysisJob() != null ? graph.getAnalysisJob().getId() : null,
                graph.getRepository() != null ? graph.getRepository().getId() : null,
                graph.getCommitSha(),
                nodeDtos,
                edgeDtos
        );
    }

    private void verifyRepositoryAccess(Repository repo, Optional<User> currentUser) {
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not have permission to access this resource");
            }
        }
    }
}
