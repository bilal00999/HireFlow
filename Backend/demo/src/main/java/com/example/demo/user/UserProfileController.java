package com.example.demo.user;

import com.example.demo.user.dto.ChangePasswordRequest;
import com.example.demo.user.dto.MeResponse;
import com.example.demo.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The authenticated candidate's own profile: read it, edit identity fields, and
 * change password. Every method resolves the user from the JWT, so no endpoint
 * accepts a userId and no one can act on another user's profile.
 *
 * Profile image and resume uploads live under {@code /api/v1/users/profile-image}
 * and {@code /api/v1/resumes} respectively.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /** Full profile for the current candidate (identity + image/resume metadata). */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    public MeResponse me() {
        return userProfileService.currentProfile();
    }

    /** Update the current candidate's name, email, phone and LinkedIn URL. */
    @PatchMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    public MeResponse updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return userProfileService.updateProfile(req);
    }

    /** Change the current candidate's password (requires the current password). */
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userProfileService.changePassword(req);
    }
}
