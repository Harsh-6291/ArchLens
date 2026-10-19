package com.archlens.backend.service;

import com.archlens.backend.detector.model.DetectedNode;
import com.archlens.backend.detector.model.DetectionResult;
import com.archlens.backend.entity.*;
import com.archlens.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GraphAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(GraphAssemblyService.class);

    private final ArchitectureGraphRepository graphRepository;
    private final GraphNodeRepository nodeRepository;
    private final GraphEdgeRepository edgeRepository;
    private final AnalysisJobRepository jobRepository;
    private final RepoRepository repoRepository;
    private final ObjectMapper objectMapper;

    public GraphAssemblyService(
            ArchitectureGraphRepository graphRepository,
            GraphNodeRepository nodeRepository,
            GraphEdgeRepository edgeRepository,
            AnalysisJobRepository jobRepository,
            RepoRepository repoRepository,
            ObjectMapper objectMapper
    ) {
        this.graphRepository = graphRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.jobRepository = jobRepository;
        this.repoRepository = repoRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ArchitectureGraph assembleAndPersistGraph(UUID jobId, UUID repoId, String commitSha, DetectionResult detectionResult) {
        log.info("Assembling architecture graph for job={}, repo={}, commit={}", jobId, repoId, commitSha);

        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        Repository repo = repoRepository.findById(repoId).orElse(null);

        if (job == null || repo == null) {
            log.warn("Cannot assemble graph: job or repo not found (job={}, repo={})", jobId, repoId);
            return null;
        }

        ArchitectureGraph graph = new ArchitectureGraph(job, repo, commitSha);
        graph = graphRepository.save(graph);

        List<DetectedNode> rawNodes = (detectionResult != null && detectionResult.nodes() != null)
                ? detectionResult.nodes()
                : List.of();

        List<GraphNode> savedNodes = new ArrayList<>();
        Map<String, GraphNode> nodesByKey = new HashMap<>();

        for (DetectedNode raw : rawNodes) {
            String metadataJson = null;
            try {
                metadataJson = objectMapper.writeValueAsString(raw.metadata());
            } catch (Exception ignored) {}

            GraphNode node = new GraphNode(
                    graph,
                    raw.id(),
                    raw.name(),
                    raw.type(),
                    raw.technology(),
                    raw.confidence(),
                    raw.sourceFile(),
                    metadataJson
            );
            node = nodeRepository.save(node);
            savedNodes.add(node);
            nodesByKey.put(node.getNodeKey(), node);
        }

        // Infer relationship edges between components
        List<GraphEdge> inferredEdges = inferEdges(graph, savedNodes);
        if (!inferredEdges.isEmpty()) {
            edgeRepository.saveAll(inferredEdges);
        }

        // Link graphId back to the AnalysisJob
        job.setGraphId(graph.getId());
        jobRepository.save(job);

        log.info("Graph assembled successfully: id={}, nodes={}, edges={}",
                graph.getId(), savedNodes.size(), inferredEdges.size());

        return graph;
    }

    private List<GraphEdge> inferEdges(ArchitectureGraph graph, List<GraphNode> nodes) {
        List<GraphEdge> edges = new ArrayList<>();
        List<GraphNode> services = new ArrayList<>();
        List<GraphNode> webApps = new ArrayList<>();
        List<GraphNode> databases = new ArrayList<>();
        List<GraphNode> caches = new ArrayList<>();
        List<GraphNode> brokers = new ArrayList<>();

        for (GraphNode node : nodes) {
            String type = node.getType();
            if ("SERVICE".equalsIgnoreCase(type)) {
                services.add(node);
            } else if ("WEB_APPLICATION".equalsIgnoreCase(type)) {
                webApps.add(node);
            } else if ("SQL_DATABASE".equalsIgnoreCase(type) || "NOSQL_DATABASE".equalsIgnoreCase(type) || "DATABASE".equalsIgnoreCase(type)) {
                databases.add(node);
            } else if ("CACHE".equalsIgnoreCase(type)) {
                caches.add(node);
            } else if ("MESSAGE_BROKER".equalsIgnoreCase(type)) {
                brokers.add(node);
            }
        }

        // 1. Web application -> backend services
        for (GraphNode web : webApps) {
            for (GraphNode svc : services) {
                edges.add(new GraphEdge(graph, web, svc, "CALLS", 0.75, "{\"inferred\":true}"));
            }
        }

        // 2. Services -> databases
        for (GraphNode svc : services) {
            for (GraphNode db : databases) {
                edges.add(new GraphEdge(graph, svc, db, "CONNECTS_TO", 0.70, "{\"inferred\":true}"));
            }
        }

        // 3. Services -> caches
        for (GraphNode svc : services) {
            for (GraphNode cache : caches) {
                edges.add(new GraphEdge(graph, svc, cache, "CONNECTS_TO", 0.70, "{\"inferred\":true}"));
            }
        }

        // 4. Services -> message brokers
        for (GraphNode svc : services) {
            for (GraphNode broker : brokers) {
                edges.add(new GraphEdge(graph, svc, broker, "PUBLISHES_SUBSCRIBES", 0.70, "{\"inferred\":true}"));
            }
        }

        return edges;
    }
}
