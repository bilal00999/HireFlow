package com.example.demo.assessment.dto;

import com.example.demo.assessment.AssessmentQuestion;

import java.util.List;

/**
 * HR-facing view of an assessment question. Includes {@code correctOption} and
 * {@code maxScore} because this is only ever returned to the owning HR account
 * (the candidate-facing {@code CandidateQuestionDto} hides those).
 */
public record QuestionDto(
        String id,
        String questionText,
        String questionType,
        List<String> options,
        Integer correctOption,
        Integer maxScore,
        Integer orderIndex
) {

    public static QuestionDto from(AssessmentQuestion q) {
        return new QuestionDto(
                q.getId().toString(),
                q.getQuestionText(),
                q.getQuestionType(),
                q.getOptions(),
                q.getCorrectOption(),
                q.getMaxScore(),
                q.getOrderIndex());
    }
}
