package com.example.demo.dashboard.dto;

/**
 * One turn of an interview transcript for the HR candidate view.
 * {@code role} is "ai" or "candidate"; {@code order} preserves conversation order.
 */
public record TranscriptMessageDto(
        String role,
        String content,
        int order
) {}
