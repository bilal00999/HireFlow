package com.example.demo.ats;

/**
 * Thrown when a stored resume cannot be read or parsed into text.
 */
public class ResumeExtractionException extends RuntimeException {
    public ResumeExtractionException(String message) {
        super(message);
    }

    public ResumeExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
