package com.example.demo.job.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for creating/updating a job (HR only). Matches 04-API-DESIGN.md.
 * Only title and description are required; the rest have sensible defaults.
 */
public record CreateJobRequest(
        @NotBlank String title,
        @NotBlank String description,
        String requirements,
        List<String> requiredSkills,
        String jobType,
        String location,
        Integer salaryMin,
        Integer salaryMax,
        Integer atsMinScore,
        Integer assessmentPassScore,
        Integer assessmentTimeLimit,
        List<String> interviewTopics,
        Integer interviewDuration,
        Integer interviewNumQuestions,
        LocalDate deadline
) {}
