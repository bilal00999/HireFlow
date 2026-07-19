package com.example.demo.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single assessment question attached to a job. Mirrors the
 * `assessment_questions` table in 03-DATABASE-DESIGN.md.
 *
 * <p>Two question types are supported. {@code MCQ} questions carry an ordered
 * list of {@link #options} and a {@link #correctOption} index for automatic
 * grading. {@code TEXT} questions are free-form and graded by the LLM. The
 * spec models {@code options} as JSONB; we map it as an {@code @ElementCollection}
 * to stay consistent with how {@code Job.requiredSkills}/{@code interviewTopics}
 * are persisted and to avoid JSONB/H2 friction in tests.
 */
@Entity
@Table(name = "assessment_questions")
@Getter
@Setter
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    /** MCQ or TEXT. */
    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    /** MCQ only: the ordered answer choices shown to the candidate. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assessment_question_options",
            joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text", columnDefinition = "text")
    private List<String> options = new ArrayList<>();

    /** MCQ only: 0-based index into {@link #options} of the correct choice. */
    @Column(name = "correct_option")
    private Integer correctOption;

    /** Points this question contributes to the raw total. */
    @Column(name = "max_score")
    private Integer maxScore = 10;

    /** Presentation order within the job's assessment. */
    @Column(name = "order_index")
    private Integer orderIndex = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
