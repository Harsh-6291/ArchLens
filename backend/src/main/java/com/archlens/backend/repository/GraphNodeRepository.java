package com.archlens.backend.repository;

import com.archlens.backend.entity.GraphNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GraphNodeRepository extends JpaRepository<GraphNode, UUID> {
    List<GraphNode> findByGraphId(UUID graphId);
}
