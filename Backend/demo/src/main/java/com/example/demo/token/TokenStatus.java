package com.example.demo.token;

/**
 * Outcome of validating a token. {@code VALID} is the only status that grants
 * access; the rest map to distinct candidate-facing messages (expired link,
 * already-used link, unknown/tampered link).
 */
public enum TokenStatus {
    VALID,
    NOT_FOUND,
    EXPIRED,
    ALREADY_USED
}
