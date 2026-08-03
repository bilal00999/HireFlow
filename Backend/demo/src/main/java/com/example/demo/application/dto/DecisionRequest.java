package com.example.demo.application.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /applications/{id}/decision} — the HR final
 * hire/reject call after the interview. {@code decision} is HIRE or REJECT;
 * {@code note} is an optional personal message included in the candidate email.
 */
public record DecisionRequest(
        @NotNull Decision decision,
        String note
) {
    public enum Decision { HIRE, REJECT }
}
