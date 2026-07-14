package com.example.demo.common;

/**
 * Thrown for business-rule violations (e.g. duplicate email, wrong password).
 * Maps to HTTP 400.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
