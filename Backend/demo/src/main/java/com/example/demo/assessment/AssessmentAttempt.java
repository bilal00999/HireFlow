package com.example.demo.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One candidate attempt at a job's assessment. Mirrors the
 * `assessment_attempts` table in 03-DATABASE-DESIGN.md.
 *
 * <p>Created when the candidate first fetches questions and finalized on
 * submit, when {@link #submittedAt}, {@link #score} and {@link #passed} are
 * populated by grading. There is at most one attempt per application because
 * the gating token is single-use.
 */
@Entity
@Table(name = "assessment_attempts")
@Getter
@Setter
public class AssessmentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Normalized 0-100 score, set after grading. */
    @Column(name = "score")
    private Integer score;

    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "time_taken_sec")
    private Integer timeTakenSec;
}
