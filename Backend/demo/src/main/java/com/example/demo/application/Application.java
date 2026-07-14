package com.example.demo.application;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A candidate's application to a job. Mirrors the `applications` table in
 * 03-DATABASE-DESIGN.md. One application per (job, candidate) pair.
 */
@Entity
@Table(
        name = "applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_application_job_user",
                columnNames = {"job_id", "user_id"})
)
@Getter
@Setter
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resume_url", nullable = false, length = 500)
    private String resumeUrl;

    @Column(name = "cover_letter", columnDefinition = "text")
    private String coverLetter;

    // Stages: APPLIED -> ATS_REVIEW -> ASSESSMENT -> INTERVIEW -> FINAL -> REJECTED
    @Column(length = 30)
    private String stage = "APPLIED";

    // REASON: ATS_FAILED, ASSESSMENT_FAILED, INTERVIEW_FAILED, TOKEN_EXPIRED
    @Column(name = "rejection_reason", length = 50)
    private String rejectionReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
