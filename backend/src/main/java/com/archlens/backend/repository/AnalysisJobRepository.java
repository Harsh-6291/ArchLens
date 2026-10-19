package com.archlens.backend.repository;

import com.archlens.backend.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    @Query("SELECT j FROM AnalysisJob j WHERE j.repository.id = :repoId AND j.status IN ('queued', 'running')")
    List<AnalysisJob> findInFlightJobs(@Param("repoId") UUID repoId);

    @Query("SELECT j FROM AnalysisJob j WHERE j.repository.id = :repoId AND j.commitSha = :commitSha AND j.status IN ('queued', 'running')")
    Optional<AnalysisJob> findInFlightJobForCommit(@Param("repoId") UUID repoId, @Param("commitSha") String commitSha);

    List<AnalysisJob> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
}
