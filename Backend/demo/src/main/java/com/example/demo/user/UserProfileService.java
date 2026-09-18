package com.example.demo.user;

import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.file.dto.ProfileImageResponse;
import com.example.demo.file.dto.ResumeResponse;
import com.example.demo.user.dto.ChangePasswordRequest;
import com.example.demo.user.dto.MeResponse;
import com.example.demo.user.dto.UpdateProfileRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Reads and updates the authenticated candidate's own profile: identity fields
 * (name, email, phone, LinkedIn), password, and a one-call view of their
 * uploaded image/resume metadata.
 *
 * Ownership always comes from {@link SecurityUtils#currentUserId()} — never a
 * request parameter — so a user can only ever read or modify their own record.
 * File upload/delete and Cloudinary access live in {@code UserFileService};
 * this service only reflects the already-stored file metadata.
 */
@Service
public class UserProfileService {

    /** Mirrors the value UserFileService exposes; never the raw (private) Cloudinary URL. */
    private static final String RESUME_DOWNLOAD_PATH = "/api/v1/resumes/download";
    private static final String CANDIDATE_ROLE = "CANDIDATE";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(UserRepository userRepository,
                              CompanyRepository companyRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public MeResponse currentProfile() {
        return toMeResponse(currentUser());
    }

    public MeResponse updateProfile(UpdateProfileRequest req) {
        User user = currentUser();

        String newEmail = req.email().trim();
        // Only guard uniqueness when the email actually changes. A candidate and a
        // company can never share an email, so both stores must be checked (as at registration).
        if (!newEmail.equalsIgnoreCase(user.getEmail()) && emailTaken(newEmail)) {
            throw new BadRequestException("Email is already registered");
        }

        user.setFullName(req.fullName().trim());
        user.setEmail(newEmail);
        user.setPhone(blankToNull(req.phone()));
        user.setLinkedinUrl(blankToNull(req.linkedinUrl()));
        userRepository.save(user);
        return toMeResponse(user);
    }

    public void changePassword(ChangePasswordRequest req) {
        User user = currentUser();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    // ---------------------------------------------------------------- helpers

    private boolean emailTaken(String email) {
        return userRepository.existsByEmail(email) || companyRepository.existsByEmail(email);
    }

    private User currentUser() {
        UUID userId = SecurityUtils.currentUserId();
        if (userId == null) {
            // Endpoints are authenticated, so this is defensive only.
            throw new BadRequestException("Authentication required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MeResponse toMeResponse(User u) {
        ProfileImageResponse image = u.getProfileImagePublicId() == null ? null
                : new ProfileImageResponse(
                        u.getProfileImageUrl(), u.getProfileImageName(),
                        u.getProfileImageType(), u.getProfileImageSize(), u.getProfileImageUpdatedAt());
        ResumeResponse resume = u.getResumePublicId() == null ? null
                : new ResumeResponse(
                        u.getResumeName(), u.getResumeType(), u.getResumeSize(),
                        u.getResumeUpdatedAt(), RESUME_DOWNLOAD_PATH);
        return new MeResponse(
                u.getId().toString(), u.getEmail(), u.getFullName(), u.getPhone(), u.getLinkedinUrl(),
                CANDIDATE_ROLE, image, resume);
    }
}
