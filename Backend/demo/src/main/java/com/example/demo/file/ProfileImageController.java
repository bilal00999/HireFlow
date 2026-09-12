package com.example.demo.file;

import com.example.demo.file.dto.ProfileImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Candidate profile-image upload/replace/delete. Ownership is taken from the JWT
 * (never a request param): every method resolves the current user via the
 * security context inside {@link UserFileService}.
 */
@RestController
@RequestMapping("/api/v1/users/profile-image")
public class ProfileImageController {

    private final UserFileService userFileService;

    public ProfileImageController(UserFileService userFileService) {
        this.userFileService = userFileService;
    }

    /** Upload or replace the current candidate's profile image (multipart field: file). */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ProfileImageResponse upload(@RequestParam("file") MultipartFile file) {
        return userFileService.uploadProfileImage(file);
    }

    /** Metadata for the current candidate's profile image (404 if none). */
    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ProfileImageResponse current() {
        return userFileService.currentProfileImage();
    }

    /** Remove the current candidate's profile image. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void delete() {
        userFileService.deleteProfileImage();
    }
}
