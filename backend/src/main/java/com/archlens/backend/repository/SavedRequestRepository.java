package com.archlens.backend.repository;

import com.archlens.backend.entity.SavedRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedRequestRepository extends JpaRepository<SavedRequest, UUID> {

    List<SavedRequest> findByApiDefinitionIdOrderByCreatedAtAsc(UUID apiDefinitionId);

    Optional<SavedRequest> findByApiDefinitionIdAndName(UUID apiDefinitionId, String name);

    boolean existsByApiDefinitionIdAndNameAndIdNot(UUID apiDefinitionId, String name, UUID excludeId);

    void deleteByApiDefinitionId(UUID apiDefinitionId);
}
