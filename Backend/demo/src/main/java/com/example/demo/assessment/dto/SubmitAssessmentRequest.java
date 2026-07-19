package com.example.demo.assessment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /assessment/:token/submit}. Each entry pairs a
 * question id with the candidate's response: {@code selectedOption} for MCQ,
 * {@code answerText} for TEXT.
 */
public record SubmitAssessmentRequest(
        @NotNull List<AnswerInput> answers
) {

    public record AnswerInput(
            @NotNull String questionId,
            Integer selectedOption,
            String answerText
    ) {}
}
