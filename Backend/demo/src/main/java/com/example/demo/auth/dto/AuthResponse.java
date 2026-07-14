package com.example.demo.auth.dto;

/**
 * Unified auth response returned by register + login.
 * `id` is the user/company UUID, `name` is full name (candidate) or company name (HR).
 */
public record AuthResponse(
        String token,
        String id,
        String email,
        String name,
        String role
) {}
