package com.example.demo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editable identity fields for the current candidate. The owner is always taken
 * from the JWT, never from this payload. {@code phone} and {@code linkedinUrl}
 * are optional — a blank value clears the stored field.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 200) String email,
        @Size(max = 30) String phone,
        @Size(max = 300) String linkedinUrl
) {}
