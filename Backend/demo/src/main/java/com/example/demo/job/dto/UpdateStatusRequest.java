package com.example.demo.job.dto;

/**
 * Request body for PATCH /jobs/:id/status.
 */
public record UpdateStatusRequest(String status) {}
