package com.example.demo.job.dto;

import java.time.LocalDate;

/**
 * Lightweight job representation for list/browse views.
 */
public record JobSummaryDto(
        String id,
        String title,
        String company,
        String jobType,
        String location,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        LocalDate deadline,
        String status
) {}
