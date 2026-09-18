package com.example.demo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password change for the current candidate. The current password must be
 * supplied and verified before the new one is accepted.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String newPassword
) {}
