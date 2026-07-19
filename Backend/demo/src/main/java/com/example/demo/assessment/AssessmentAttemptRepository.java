package com.example.demo.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, UUID> {

    /** At most one attempt per application (the gating token is single-use). */
    Optional<AssessmentAttempt> findByApplicationId(UUID applicationId);
}
