package com.example.demo.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    /** At most one interview session per application (the gating token is single-use). */
    Optional<InterviewSession> findByApplicationId(UUID applicationId);
}
