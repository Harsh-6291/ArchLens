package com.archlens.backend.repository;

import com.archlens.backend.entity.RequestExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestExecutionRepository extends JpaRepository<RequestExecution, UUID> {

    List<RequestExecution> findByApiDefinitionIdOrderByExecutedAtDesc(UUID apiDefinitionId);

    Page<RequestExecution> findByApiDefinitionIdOrderByExecutedAtDesc(UUID apiDefinitionId, Pageable pageable);
}
