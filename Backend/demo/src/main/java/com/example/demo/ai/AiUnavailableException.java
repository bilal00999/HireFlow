package com.example.demo.ai;

/**
 * Thrown when no AI provider can service a request (unreachable, timed out,
 * or returned an error). Callers may fall back to manual review.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
