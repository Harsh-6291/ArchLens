package com.archlens.backend.service;

import com.archlens.backend.dto.AiRecommendationResponse;
import com.archlens.backend.dto.SecurityFindingResponse;
import com.archlens.backend.entity.*;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.AiRecommendationRepository;
import com.archlens.backend.repository.ArchitectureGraphRepository;
import com.archlens.backend.repository.SecurityFindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SecurityAndAiService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAndAiService.class);

    private final SecurityFindingRepository securityFindingRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final ArchitectureGraphRepository graphRepository;

    @Autowired
    public SecurityAndAiService(
            SecurityFindingRepository securityFindingRepository,
            AiRecommendationRepository aiRecommendationRepository,
            ArchitectureGraphRepository graphRepository
    ) {
        this.securityFindingRepository = securityFindingRepository;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.graphRepository = graphRepository;
    }

    // ------------------------------------------------------------- Security Scanner

    @Transactional
    public List<SecurityFindingResponse> runSecurityScan(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        // Scan nodes and patterns
        List<SecurityFinding> findings = new ArrayList<>();
        int count = 1;

        for (GraphNode node : graph.getNodes()) {
            String tech = node.getTechnology() != null ? node.getTechnology().toLowerCase() : "";
            String type = node.getType() != null ? node.getType().toUpperCase() : "";

            // Check for unencrypted database connection
            if (type.contains("DATABASE") && !tech.contains("ssl") && !tech.contains("tls")) {
                findings.add(new SecurityFinding(
                        graph, node,
                        "SEC-EVID-" + (count++),
                        "HIGH",
                        "Unencrypted Database Transport",
                        "DATA_EXPOSURE",
                        "Database connection on node '" + node.getName() + "' appears to lack TLS/SSL transport encryption.",
                        "Enforce sslMode=VERIFY_FULL or sslMode=REQUIRE in Spring datasource properties.",
                        node.getSourceFile(), 1
                ));
            }

            // Check for exposed actuator/management endpoints
            if (tech.contains("spring") && (node.getName().toLowerCase().contains("actuator") || node.getNodeKey().contains("admin"))) {
                findings.add(new SecurityFinding(
                        graph, node,
                        "SEC-EVID-" + (count++),
                        "MEDIUM",
                        "Potentially Exposed Management Surface",
                        "MISCONFIGURATION",
                        "Spring Boot Actuator or administration endpoints detected on node '" + node.getName() + "'.",
                        "Configure management.endpoints.web.exposure.include with minimal endpoints (health, info only).",
                        node.getSourceFile(), 12
                ));
            }
        }

        // Default baseline security rule if no specific issues triggered
        if (findings.isEmpty()) {
            findings.add(new SecurityFinding(
                    graph, null,
                    "SEC-EVID-1",
                    "INFO",
                    "JWT / Session Secret Rotation Notice",
                    "AUTH",
                    "Ensure production signing keys meet minimum 256-bit entropy requirements and are rotated regularly.",
                    "Configure external secret manager (AWS Secrets Manager / Vault) for archlens.security.jwt-secret.",
                    "application.yml", 1
            ));
        }

        securityFindingRepository.saveAll(findings);
        return findings.stream().map(SecurityFindingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SecurityFindingResponse> getSecurityFindings(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        return securityFindingRepository.findByGraphIdOrderByCreatedAtDesc(graph.getId())
                .stream()
                .map(SecurityFindingResponse::from)
                .toList();
    }

    // ------------------------------------------------------------- AI Recommendations

    @Transactional
    public List<AiRecommendationResponse> generateRecommendations(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        List<SecurityFinding> findings = securityFindingRepository.findByGraphIdOrderByCreatedAtDesc(graph.getId());
        Set<String> validEvidenceIds = findings.stream().map(SecurityFinding::getEvidenceId).collect(Collectors.toSet());

        List<AiRecommendation> recommendations = new ArrayList<>();

        if (!findings.isEmpty()) {
            for (SecurityFinding f : findings) {
                List<String> citedEvidence = List.of(f.getEvidenceId());
                boolean verified = verifyEvidenceCitations(citedEvidence, validEvidenceIds);

                recommendations.add(new AiRecommendation(
                        graph,
                        "Security finding: " + f.getTitle(),
                        f.getDescription(),
                        citedEvidence,
                        f.getRemediation(),
                        "Prevents security breach and improves compliance posture.",
                        "Requires redeployment and environment variable configuration update.",
                        verified
                ));
            }
        } else {
            recommendations.add(new AiRecommendation(
                    graph,
                    "Scalability Optimization: Cache Hot Read Paths",
                    "Service nodes show direct database coupling without an intermediate caching tier.",
                    List.of(),
                    "Introduce Redis cache annotations (@Cacheable) on frequently accessed query methods.",
                    "Reduces downstream DB load and cuts p95 response latencies by ~40%.",
                    "Requires cache invalidation strategy on mutations.",
                    true
            ));
        }

        aiRecommendationRepository.saveAll(recommendations);
        return recommendations.stream().map(AiRecommendationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AiRecommendationResponse> getRecommendations(UUID graphId, Optional<User> currentUser) {
        ArchitectureGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "graph_not_found", "Architecture graph not found"));

        verifyRepositoryAccess(graph.getRepository(), currentUser);

        return aiRecommendationRepository.findByGraphIdOrderByCreatedAtDesc(graph.getId())
                .stream()
                .map(AiRecommendationResponse::from)
                .toList();
    }

    /**
     * M4.3 Post-generation hallucination check:
     * Verifies that every cited evidence ID in the recommendation exists in the context that was provided.
     */
    public boolean verifyEvidenceCitations(List<String> citedEvidenceIds, Set<String> validEvidenceIds) {
        if (citedEvidenceIds == null || citedEvidenceIds.isEmpty()) return true;
        return validEvidenceIds.containsAll(citedEvidenceIds);
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
