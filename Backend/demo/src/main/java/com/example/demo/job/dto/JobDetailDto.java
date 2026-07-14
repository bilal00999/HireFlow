package com.example.demo.job.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Full public job detail. Deliberately omits internal pipeline config
 * (ATS threshold, assessment/interview settings) per 04-API-DESIGN.md.
 */
public record JobDetailDto(
        String id,
        String title,
        String company,
        String description,
        String requirements,
        List<String> requiredSkills,
        String jobType,
        String location,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        LocalDate deadline,
        String status
) {}
