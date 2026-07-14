package com.example.demo.application.dto;

/**
 * Response returned right after a candidate applies. Matches
 * POST /applications in 04-API-DESIGN.md.
 */
public record ApplyResponse(
        String applicationId,
        String stage,
        String message
) {}
