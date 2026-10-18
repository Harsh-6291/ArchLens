package com.archlens.backend.repository;

import com.archlens.backend.entity.SystemGithubCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface SystemGithubCredentialRepository extends JpaRepository<SystemGithubCredential, UUID> {
    
    @Query("SELECT s FROM SystemGithubCredential s WHERE s.rotatedAt IS NULL ORDER BY s.createdAt DESC LIMIT 1")
    Optional<SystemGithubCredential> findActiveCredential();
}
