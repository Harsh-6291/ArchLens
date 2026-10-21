package com.archlens.backend.repository;

import com.archlens.backend.entity.ArchitectureVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArchitectureVersionRepository extends JpaRepository<ArchitectureVersion, UUID> {

    List<ArchitectureVersion> findByGraphIdOrderByCreatedAtDesc(UUID graphId);
}
