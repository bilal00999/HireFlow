package com.example.demo.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code GET /interview/verify/:token}. On success carries the
 * details the gate screen needs; on failure carries only a machine-readable
 * reason. Null fields are omitted so the two shapes stay clean on the wire
 * (mirrors the assessment VerifyTokenResponse).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyInterviewResponse(
        boolean valid,
        String reason,
        String jobTitle,
        Integer durationMinutes
) {

    public static VerifyInterviewResponse valid(String jobTitle, int durationMinutes) {
        return new VerifyInterviewResponse(true, null, jobTitle, durationMinutes);
    }

    public static VerifyInterviewResponse invalid(String reason) {
        return new VerifyInterviewResponse(false, reason, null, null);
    }
}
