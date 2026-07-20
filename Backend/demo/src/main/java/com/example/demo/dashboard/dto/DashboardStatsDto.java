package com.example.demo.dashboard.dto;

/**
 * Overview stats for the HR dashboard landing page
 * (GET /api/v1/hr/dashboard). All counts are scoped to the
 * authenticated HR user's company.
 */
public record DashboardStatsDto(
        long activeJobs,
        long totalApplications,
        long inAssessment,
        long inInterview,
        long readyForReview
) {}
