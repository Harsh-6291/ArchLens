package com.archlens.backend.repository;

import com.archlens.backend.entity.SecurityFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, UUID> {

    List<SecurityFinding> findByGraphIdOrderByCreatedAtDesc(UUID graphId);

    List<SecurityFinding> findByGraphNodeIdOrderByCreatedAtDesc(UUID graphNodeId);

    long countByGraphId(UUID graphId);
}
