package com.archlens.backend.repository;

import com.archlens.backend.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepoRepository extends JpaRepository<Repository, UUID> {
    Optional<Repository> findByFullName(String fullName);
    Optional<Repository> findByGithubRepoId(Long githubRepoId);
    List<Repository> findByUserId(UUID userId);
}
