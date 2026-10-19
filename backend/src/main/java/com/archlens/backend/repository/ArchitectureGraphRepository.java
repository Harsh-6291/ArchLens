package com.archlens.backend.repository;

import com.archlens.backend.entity.ArchitectureGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchitectureGraphRepository extends JpaRepository<ArchitectureGraph, UUID> {
    Optional<ArchitectureGraph> findByAnalysisJobId(UUID analysisJobId);
    Optional<ArchitectureGraph> findTopByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
}
