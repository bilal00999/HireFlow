package com.example.demo.application.dto;

import java.time.LocalDateTime;

/**
 * A candidate's own application in list form (GET /applications/my).
 * Carries the minimal job reference the candidate dashboard needs.
 */
public record MyApplicationDto(
        String id,
        JobRef job,
        String stage,
        String rejectionReason,
        LocalDateTime appliedAt
) {
    public record JobRef(String id, String title, String company) {}
}
