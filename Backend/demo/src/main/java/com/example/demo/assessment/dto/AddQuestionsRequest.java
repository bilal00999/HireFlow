package com.example.demo.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /jobs/:id/questions} (HR only). Matches
 * 04-API-DESIGN.md: a batch of questions appended to a job's assessment.
 */
public record AddQuestionsRequest(
        @NotEmpty @Valid List<QuestionInput> questions
) {

    /**
     * One question to add. MCQ questions must carry {@code options} and a
     * {@code correctOption} index; the service validates this cross-field rule
     * since it depends on {@code questionType}.
     */
    public record QuestionInput(
            @NotBlank String questionText,
            @NotNull String questionType,   // MCQ or TEXT
            List<String> options,
            Integer correctOption,
            Integer maxScore
    ) {}
}
