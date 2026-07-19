package com.example.demo.assessment.dto;

import java.util.List;

/**
 * Response for {@code GET /assessment/:token/questions}: the job title, the
 * time limit in minutes, and the ordered questions (without answer keys).
 */
public record AssessmentQuestionsResponse(
        String jobTitle,
        int timeLimit,
        List<CandidateQuestionDto> questions
) {}
