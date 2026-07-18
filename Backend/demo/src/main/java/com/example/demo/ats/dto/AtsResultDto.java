package com.example.demo.ats.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ATS score breakdown returned to HR for a single application
 * (GET /api/v1/ats/{applicationId}).
 */
public record AtsResultDto(
        String applicationId,
        int score,
        Integer skillsMatch,
        Integer experienceMatch,
        Integer educationMatch,
        Integer keywordMatch,
        String summary,
        List<String> matchedSkills,
        List<String> missingSkills,
        boolean passed,
        LocalDateTime reviewedAt
) {}
