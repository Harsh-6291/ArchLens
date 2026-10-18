package com.archlens.backend.repository;

import com.archlens.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByGithubId(Long githubId);
    Optional<User> findByGithubUsername(String githubUsername);
}
