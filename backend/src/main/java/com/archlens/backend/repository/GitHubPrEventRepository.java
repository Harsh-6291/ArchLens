package com.archlens.backend.repository;

import com.archlens.backend.entity.GitHubPrEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GitHubPrEventRepository extends JpaRepository<GitHubPrEvent, UUID> {
    List<GitHubPrEvent> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
    List<GitHubPrEvent> findByRepositoryIdAndPrNumberOrderByCreatedAtDesc(UUID repositoryId, Integer prNumber);
}
