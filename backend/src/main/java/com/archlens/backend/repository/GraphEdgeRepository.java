package com.archlens.backend.repository;

import com.archlens.backend.entity.GraphEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GraphEdgeRepository extends JpaRepository<GraphEdge, UUID> {
    List<GraphEdge> findByGraphId(UUID graphId);
}
