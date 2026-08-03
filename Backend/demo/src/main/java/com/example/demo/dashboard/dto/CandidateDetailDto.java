package com.example.demo.dashboard.dto;

import java.util.List;

/**
 * Consolidated scorecard for one candidate, for the HR candidate-detail view
 * (GET /api/v1/hr/candidate/{applicationId}). Pulls every stage's result into a
 * single payload: identity, current stage, the ATS/assessment/interview scores,
 * the AI interview report, and the full interview transcript. Any stage the
 * candidate hasn't reached comes back null so the UI can render "not yet".
 */
public record CandidateDetailDto(
        String applicationId,
        String candidateName,
        String candidateEmail,
        String jobId,
        String jobTitle,
        String stage,
        String rejectionReason,
        String resumeUrl,
        String coverLetter,
        AtsSection ats,
        Integer assessmentScore,
        InterviewSection interview,
        List<TranscriptMessageDto> transcript
) {
    /** ATS resume-screen result; null if scoring hasn't run yet. */
    public record AtsSection(
            int score,
            boolean passed,
            String summary,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {}

    /** AI interview outcome; null if the interview isn't complete. */
    public record InterviewSection(
            Integer score,
            Boolean passed,
            String recommendation,
            String summary,
            List<String> strengths,
            List<String> weaknesses
    ) {}
}
