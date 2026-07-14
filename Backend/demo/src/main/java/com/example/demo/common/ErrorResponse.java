package com.example.demo.common;

import java.time.Instant;

/**
 * Standard error body used across all endpoints (see 04-API-DESIGN.md).
 */
public record ErrorResponse(
        String error,
        String message,
        String timestamp,
        String path
) {
    public static ErrorResponse of(String error, String message, String path) {
        return new ErrorResponse(error, message, Instant.now().toString(), path);
    }
}
