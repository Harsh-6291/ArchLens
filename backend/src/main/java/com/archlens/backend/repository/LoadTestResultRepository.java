package com.archlens.backend.repository;

import com.archlens.backend.entity.LoadTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoadTestResultRepository extends JpaRepository<LoadTestResult, UUID> {

    Optional<LoadTestResult> findByLoadTestId(UUID loadTestId);
}
