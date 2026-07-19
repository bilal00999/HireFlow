package com.example.demo.assessment.dto;

import com.example.demo.assessment.AssessmentQuestion;

import java.util.List;

/**
 * Candidate-facing view of a question. Deliberately omits {@code correctOption}
 * so the answer key is never shipped to the browser; MCQ options are included
 * (the candidate needs to see the choices) but TEXT questions carry none.
 */
public record CandidateQuestionDto(
        String id,
        String questionText,
        String questionType,
        List<String> options,
        Integer maxScore
) {

    public static CandidateQuestionDto from(AssessmentQuestion q) {
        boolean isMcq = "MCQ".equals(q.getQuestionType());
        return new CandidateQuestionDto(
                q.getId().toString(),
                q.getQuestionText(),
                q.getQuestionType(),
                isMcq ? q.getOptions() : List.of(),
                q.getMaxScore());
    }
}
