package com.example.demo.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, UUID> {

    /** All answers submitted within one attempt. */
    List<AssessmentAnswer> findByAttemptId(UUID attemptId);
}
