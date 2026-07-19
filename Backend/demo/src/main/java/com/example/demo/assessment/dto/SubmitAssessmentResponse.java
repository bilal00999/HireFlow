package com.example.demo.assessment.dto;

/**
 * Response for {@code POST /assessment/:token/submit}. Grading runs
 * asynchronously, so this only confirms receipt; results reach the candidate
 * by email.
 */
public record SubmitAssessmentResponse(
        boolean submitted,
        String message
) {

    public static SubmitAssessmentResponse ok() {
        return new SubmitAssessmentResponse(true,
                "Assessment submitted. You will receive an email with your results.");
    }
}
