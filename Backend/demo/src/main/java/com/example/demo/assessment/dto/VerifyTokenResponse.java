package com.example.demo.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code GET /assessment/verify/:token}. On success carries the
 * details the gate screen needs; on failure carries only a machine-readable
 * reason. Null fields are omitted so the two shapes stay clean on the wire.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyTokenResponse(
        boolean valid,
        String reason,
        String jobTitle,
        Integer questionCount,
        Integer timeLimit
) {

    public static VerifyTokenResponse valid(String jobTitle, int questionCount, int timeLimit) {
        return new VerifyTokenResponse(true, null, jobTitle, questionCount, timeLimit);
    }

    public static VerifyTokenResponse invalid(String reason) {
        return new VerifyTokenResponse(false, reason, null, null, null);
    }
}
