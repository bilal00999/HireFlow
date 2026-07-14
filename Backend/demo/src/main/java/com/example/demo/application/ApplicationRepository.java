package com.example.demo.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /** Enforces "one application per job per user" at the service layer. */
    boolean existsByJobIdAndUserId(UUID jobId, UUID userId);

    /** All applications submitted by one candidate, newest first. */
    List<Application> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** All applicants for one job, newest first (HR view). */
    List<Application> findByJobIdOrderByCreatedAtDesc(UUID jobId);
}
