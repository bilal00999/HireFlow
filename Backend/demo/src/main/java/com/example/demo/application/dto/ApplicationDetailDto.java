package com.example.demo.application.dto;

import java.time.LocalDateTime;

/**
 * Full application detail for candidate/HR review (GET /applications/{id}).
 */
public record ApplicationDetailDto(
        String id,
        JobRef job,
        CandidateRef candidate,
        String resumeUrl,
        String coverLetter,
        String stage,
        String rejectionReason,
        LocalDateTime appliedAt
) {
    public record JobRef(String id, String title, String company) {}
    public record CandidateRef(String id, String fullName, String email) {}
}