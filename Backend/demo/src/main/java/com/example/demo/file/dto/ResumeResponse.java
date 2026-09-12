package com.example.demo.file.dto;

import java.time.LocalDateTime;

/**
 * Clean resume response. Deliberately does NOT expose the Cloudinary URL —
 * resumes are private. {@code downloadUrl} points at our own backend endpoint,
 * which authorizes the caller and then redirects to a short-lived signed URL.
 */
public record ResumeResponse(
        String fileName,
        String contentType,
        Long size,
        LocalDateTime updatedAt,
        String downloadUrl
) {}
