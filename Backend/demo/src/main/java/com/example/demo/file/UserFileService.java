package com.example.demo.file;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.FileStorageException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.file.CloudinaryStorageService.StoredFile;
import com.example.demo.file.dto.ProfileImageResponse;
import com.example.demo.file.dto.ResumeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates profile-image and resume storage for the authenticated candidate:
 * server-side validation, upload to Cloudinary, persistence of the metadata on
 * the existing {@link User} entity, safe replace ordering, and orphan cleanup.
 *
 * Ownership always comes from {@link SecurityUtils#currentUserId()} — the caller
 * cannot pass a userId, so no one can target another user's folder.
 */
@Service
public class UserFileService {

    private static final Logger log = LoggerFactory.getLogger(UserFileService.class);

    static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;   // 2 MB
    static final long MAX_RESUME_BYTES = 5L * 1024 * 1024;  // 5 MB

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp");

    private static final Set<String> RESUME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> RESUME_EXTS = Set.of("pdf", "docx");

    // Kept in sync with how each kind is uploaded, so deletes target the right asset.
    private static final String IMAGE_RESOURCE_TYPE = "image";
    private static final String IMAGE_DELIVERY_TYPE = "upload";
    private static final String RESUME_RESOURCE_TYPE = "raw";
    private static final String RESUME_DELIVERY_TYPE = "authenticated";

    private final CloudinaryStorageService storage;
    private final UserRepository userRepository;

    public UserFileService(CloudinaryStorageService storage, UserRepository userRepository) {
        this.storage = storage;
        this.userRepository = userRepository;
    }

    // ---------------------------------------------------------------- profile image

    public ProfileImageResponse uploadProfileImage(MultipartFile file) {
        User user = currentUser();
        validate(file, MAX_IMAGE_BYTES, IMAGE_TYPES, IMAGE_EXTS, "Profile image", "a JPG, PNG or WEBP image");

        String oldPublicId = user.getProfileImagePublicId();

        // 1) upload new  -> 2) persist  -> 3) delete old (only after success)
        StoredFile stored = storage.uploadProfileImage(user.getId(), file);
        user.setProfileImageUrl(stored.secureUrl());
        user.setProfileImagePublicId(stored.publicId());
        user.setProfileImageName(originalName(file));
        user.setProfileImageType(file.getContentType());
        user.setProfileImageSize(file.getSize());
        user.setProfileImageUpdatedAt(LocalDateTime.now());
        persistOrCleanup(user, stored.publicId(), IMAGE_RESOURCE_TYPE, IMAGE_DELIVERY_TYPE);

        if (isReplacement(oldPublicId, stored.publicId())) {
            safeDelete(oldPublicId, IMAGE_RESOURCE_TYPE, IMAGE_DELIVERY_TYPE);
        }
        return toProfileImageResponse(user);
    }

    public ProfileImageResponse currentProfileImage() {
        User user = currentUser();
        if (user.getProfileImagePublicId() == null) {
            throw new ResourceNotFoundException("No profile image found");
        }
        return toProfileImageResponse(user);
    }

    public void deleteProfileImage() {
        User user = currentUser();
        String publicId = user.getProfileImagePublicId();
        if (publicId == null) {
            throw new ResourceNotFoundException("No profile image to delete");
        }
        // Delete the asset first (surfaces failures); only then clear the reference,
        // so a storage outage leaves both the file and the DB row intact for a retry.
        storage.delete(publicId, IMAGE_RESOURCE_TYPE, IMAGE_DELIVERY_TYPE);
        user.setProfileImageUrl(null);
        user.setProfileImagePublicId(null);
        user.setProfileImageName(null);
        user.setProfileImageType(null);
        user.setProfileImageSize(null);
        user.setProfileImageUpdatedAt(null);
        userRepository.save(user);
    }

    // ---------------------------------------------------------------- resume

    public ResumeResponse uploadResume(MultipartFile file) {
        User user = currentUser();
        validate(file, MAX_RESUME_BYTES, RESUME_TYPES, RESUME_EXTS, "Resume", "a PDF or DOCX file");

        String oldPublicId = user.getResumePublicId();

        StoredFile stored = storage.uploadResume(user.getId(), file);
        user.setResumeUrl(stored.secureUrl());
        user.setResumePublicId(stored.publicId());
        user.setResumeName(originalName(file));
        user.setResumeType(file.getContentType());
        user.setResumeSize(file.getSize());
        user.setResumeUpdatedAt(LocalDateTime.now());
        persistOrCleanup(user, stored.publicId(), RESUME_RESOURCE_TYPE, RESUME_DELIVERY_TYPE);

        if (isReplacement(oldPublicId, stored.publicId())) {
            safeDelete(oldPublicId, RESUME_RESOURCE_TYPE, RESUME_DELIVERY_TYPE);
        }
        return toResumeResponse(user);
    }

    public ResumeResponse currentResume() {
        User user = currentUser();
        if (user.getResumePublicId() == null) {
            throw new ResourceNotFoundException("No resume found");
        }
        return toResumeResponse(user);
    }

    /** Authorizes the owner and returns a signed, short-lived delivery URL for their resume. */
    public String currentResumeSignedUrl() {
        User user = currentUser();
        if (user.getResumePublicId() == null) {
            throw new ResourceNotFoundException("No resume found");
        }
        return storage.signedResumeUrl(user.getResumePublicId());
    }

    public void deleteResume() {
        User user = currentUser();
        String publicId = user.getResumePublicId();
        if (publicId == null) {
            throw new ResourceNotFoundException("No resume to delete");
        }
        storage.delete(publicId, RESUME_RESOURCE_TYPE, RESUME_DELIVERY_TYPE);
        user.setResumeUrl(null);
        user.setResumePublicId(null);
        user.setResumeName(null);
        user.setResumeType(null);
        user.setResumeSize(null);
        user.setResumeUpdatedAt(null);
        userRepository.save(user);
    }

    // ---------------------------------------------------------------- helpers

    private User currentUser() {
        UUID userId = SecurityUtils.currentUserId();
        if (userId == null) {
            // Endpoints are authenticated, so this is defensive only.
            throw new BadRequestException("Authentication required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void validate(MultipartFile file, long maxBytes, Set<String> allowedTypes,
                          Set<String> allowedExts, String label, String allowedDesc) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(label + " file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(label + " must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new BadRequestException(label + " must be " + allowedDesc);
        }
        String ext = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (ext == null || !allowedExts.contains(ext.toLowerCase())) {
            throw new BadRequestException(label + " must be " + allowedDesc);
        }
    }

    /**
     * Saves the user; if persistence fails after a successful upload, removes the
     * just-uploaded file so we don't leave an orphan in Cloudinary, then rethrows.
     */
    private void persistOrCleanup(User user, String newPublicId, String resourceType, String deliveryType) {
        try {
            userRepository.save(user);
        } catch (RuntimeException e) {
            try {
                storage.delete(newPublicId, resourceType, deliveryType);
            } catch (RuntimeException cleanupEx) {
                log.error("Failed to clean up orphaned {} file '{}' after a DB error; it may need manual removal",
                        resourceType, newPublicId, cleanupEx);
            }
            throw new FileStorageException("Could not save the uploaded file information", e);
        }
    }

    /** Best-effort delete of a replaced file: a failure must not fail the successful upload. */
    private void safeDelete(String publicId, String resourceType, String deliveryType) {
        try {
            storage.delete(publicId, resourceType, deliveryType);
        } catch (RuntimeException e) {
            log.warn("Failed to delete replaced {} file '{}'; it may need manual removal",
                    resourceType, publicId, e);
        }
    }

    private boolean isReplacement(String oldPublicId, String newPublicId) {
        return oldPublicId != null && !oldPublicId.equals(newPublicId);
    }

    private String originalName(MultipartFile file) {
        String name = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        return name.isBlank() ? "file" : name;
    }

    private ProfileImageResponse toProfileImageResponse(User u) {
        return new ProfileImageResponse(
                u.getProfileImageUrl(), u.getProfileImageName(),
                u.getProfileImageType(), u.getProfileImageSize(), u.getProfileImageUpdatedAt());
    }

    private ResumeResponse toResumeResponse(User u) {
        return new ResumeResponse(
                u.getResumeName(), u.getResumeType(), u.getResumeSize(),
                u.getResumeUpdatedAt(), "/api/v1/resumes/download");
    }
}
