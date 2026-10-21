package com.archlens.backend.repository;

import com.archlens.backend.entity.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, UUID> {

    List<AiRecommendation> findByGraphIdOrderByCreatedAtDesc(UUID graphId);
}
