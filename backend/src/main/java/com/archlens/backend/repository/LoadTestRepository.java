package com.archlens.backend.repository;

import com.archlens.backend.entity.LoadTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoadTestRepository extends JpaRepository<LoadTest, UUID> {

    List<LoadTest> findByApiDefinitionIdOrderByCreatedAtDesc(UUID apiDefinitionId);

    @Query("SELECT lt FROM LoadTest lt " +
           "JOIN FETCH lt.apiDefinition ad " +
           "JOIN FETCH ad.graph g " +
           "JOIN FETCH g.repository r " +
           "WHERE lt.id = :id")
    Optional<LoadTest> findWithDetailsById(@Param("id") UUID id);
}
