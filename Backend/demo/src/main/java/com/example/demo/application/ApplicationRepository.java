package com.example.demo.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /** Enforces "one application per job per user" at the service layer. */
    boolean existsByJobIdAndUserId(UUID jobId, UUID userId);

    /** All applications submitted by one candidate, newest first. */
    List<Application> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** All applicants for one job, newest first (HR view). */
    List<Application> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    /** Total applications across all of a company's jobs (HR dashboard). */
    @Query("""
            SELECT COUNT(a) FROM Application a
            WHERE a.jobId IN (SELECT j.id FROM Job j WHERE j.companyId = :companyId)
            """)
    long countByCompanyId(@Param("companyId") UUID companyId);

    /** Applications in a given stage across all of a company's jobs (HR dashboard). */
    @Query("""
            SELECT COUNT(a) FROM Application a
            WHERE a.stage = :stage
              AND a.jobId IN (SELECT j.id FROM Job j WHERE j.companyId = :companyId)
            """)
    long countByCompanyIdAndStage(@Param("companyId") UUID companyId,
                                  @Param("stage") String stage);

    /**
     * Per-stage application counts for one job, as [stage, count] rows.
     * Stages with zero applications are simply absent; the service fills those in.
     */
    @Query("""
            SELECT a.stage, COUNT(a) FROM Application a
            WHERE a.jobId = :jobId
            GROUP BY a.stage
            """)
    List<Object[]> countByStageForJob(@Param("jobId") UUID jobId);
}
