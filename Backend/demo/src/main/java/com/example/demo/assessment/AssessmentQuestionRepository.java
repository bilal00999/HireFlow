package com.example.demo.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, UUID> {

    /** All questions for a job in presentation order. */
    List<AssessmentQuestion> findByJobIdOrderByOrderIndexAsc(UUID jobId);

    long countByJobId(UUID jobId);
}
