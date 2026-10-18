package com.archlens.backend.repository;

import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GithubCredentialRepository extends JpaRepository<GithubCredential, UUID> {
    Optional<GithubCredential> findByUser(User user);
    Optional<GithubCredential> findByUserId(UUID userId);
}
