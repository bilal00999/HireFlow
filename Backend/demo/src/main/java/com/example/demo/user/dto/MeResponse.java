package com.example.demo.user.dto;

import com.example.demo.file.dto.ProfileImageResponse;
import com.example.demo.file.dto.ResumeResponse;

/**
 * Full profile for the authenticated candidate, hydrated in a single call:
 * identity fields plus the current profile image and resume metadata. Each of
 * {@code profileImage} / {@code resume} is null when nothing has been uploaded.
 * Never carries the password hash or the private resume storage URL.
 */
public record MeResponse(
        String id,
        String email,
        String fullName,
        String phone,
        String linkedinUrl,
        String role,
        ProfileImageResponse profileImage,
        ResumeResponse resume
) {}
