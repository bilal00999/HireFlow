package com.example.demo.file.dto;

import java.time.LocalDateTime;

/**
 * Clean profile-image response — never the raw Cloudinary payload.
 * {@code url} is the public secure delivery URL (safe to expose for images).
 */
public record ProfileImageResponse(
        String url,
        String fileName,
        String contentType,
        Long size,
        LocalDateTime updatedAt
) {}
