package com.example.demo.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A single answer within an {@link AssessmentAttempt}. Mirrors the
 * `assessment_answers` table in 03-DATABASE-DESIGN.md.
 *
 * <p>For MCQ questions {@link #selectedOption} and {@link #isCorrect} are set
 * by automatic grading. For TEXT questions {@link #answerText} holds the
 * candidate's response and {@link #aiScore}/{@link #aiFeedback} are filled in
 * by the LLM grader.
 */
@Entity
@Table(name = "assessment_answers")
@Getter
@Setter
public class AssessmentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    /** MCQ only: 0-based index the candidate selected. */
    @Column(name = "selected_option")
    private Integer selectedOption;

    /** TEXT only: points awarded by the LLM (0..question max score). */
    @Column(name = "ai_score")
    private Integer aiScore;

    @Column(name = "ai_feedback", columnDefinition = "text")
    private String aiFeedback;

    /** MCQ only: whether {@link #selectedOption} matched the correct option. */
    @Column(name = "is_correct")
    private Boolean isCorrect;
}
