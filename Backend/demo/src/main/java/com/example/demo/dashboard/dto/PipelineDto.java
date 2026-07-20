package com.example.demo.dashboard.dto;

import com.example.demo.application.dto.ApplicantDto;

import java.util.List;
import java.util.Map;

/**
 * Full hiring pipeline for one job (GET /api/v1/hr/pipeline/{jobId}).
 * {@code stages} maps every stage name to its applicant count (zero-filled),
 * and {@code candidates} is the flat applicant list, newest first.
 */
public record PipelineDto(
        String jobId,
        String jobTitle,
        Map<String, Long> stages,
        List<ApplicantDto> candidates
) {}
