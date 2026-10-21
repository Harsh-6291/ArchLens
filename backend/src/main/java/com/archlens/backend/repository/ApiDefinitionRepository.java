package com.archlens.backend.repository;

import com.archlens.backend.entity.ApiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, UUID> {

    @Query("SELECT a FROM ApiDefinition a JOIN FETCH a.graph g JOIN FETCH g.repository WHERE a.id = :id")
    Optional<ApiDefinition> findWithGraphById(@Param("id") UUID id);

    List<ApiDefinition> findByGraphId(UUID graphId);

    List<ApiDefinition> findByGraphNodeId(UUID graphNodeId);

    void deleteByGraphId(UUID graphId);
}
