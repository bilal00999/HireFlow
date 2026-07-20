package com.example.demo.interview;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One AI interview session per application. Mirrors the `interview_sessions`
 * table in 03-DATABASE-DESIGN.md.
 *
 * <p>Created when the candidate starts the interview and finalized when the AI
 * signals completion, at which point {@link #endedAt}, {@link #overallScore},
 * {@link #aiReport} and {@link #passed} are populated. There is at most one
 * session per application because the gating token is single-use.
 */
@Entity
@Table(name = "interview_sessions")
@Getter
@Setter
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** IN_PROGRESS, COMPLETED, or ABANDONED. */
    @Column(length = 20)
    private String status = "IN_PROGRESS";

    /** Normalized 0-100 score, set when the interview completes. */
    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "ai_report", columnDefinition = "text")
    private String aiReport;

    @Column(name = "passed")
    private Boolean passed;
}
