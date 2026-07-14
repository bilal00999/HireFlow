package com.example.demo.application.dto;

import java.time.LocalDateTime;

/**
 * One applicant row for the HR "applicants for a job" view
 * (GET /applications/job/{jobId}). Includes candidate contact info
 * and a link to the uploaded resume.
 */
public record ApplicantDto(
        String id,
        String candidateName,
        String candidateEmail,
        String resumeUrl,
        String coverLetter,
        String stage,
        String rejectionReason,
        LocalDateTime appliedAt
) {}
