package com.example.demo.file;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.demo.common.FileStorageException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable wrapper around the Cloudinary SDK. Knows nothing about users or the
 * database — it just uploads/deletes bytes and signs delivery URLs.
 *
 * <ul>
 *   <li>Profile images: {@code resource_type=image}, {@code type=upload} — public,
 *       delivered directly via the returned secure URL.</li>
 *   <li>Resumes: {@code resource_type=raw}, {@code type=authenticated} — NOT
 *       publicly reachable; delivery requires a backend-signed URL
 *       ({@link #signedResumeUrl}). This keeps personal documents private.</li>
 * </ul>
 *
 * Files are foldered per authenticated user id, so one user can never write into
 * another user's folder (the id comes from the security context, never the request).
 */
@Service
public class CloudinaryStorageService {

    static final String PROFILE_IMAGE_FOLDER = "hireflow/profile-images";
    static final String RESUME_FOLDER = "hireflow/resumes";

    private final Cloudinary cloudinary;

    public CloudinaryStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** Uploads a public profile image under {@code hireflow/profile-images/{userId}}. */
    public StoredFile uploadProfileImage(UUID userId, MultipartFile file) {
        return upload(file, ObjectUtils.asMap(
                "folder", PROFILE_IMAGE_FOLDER + "/" + userId,
                "resource_type", "image",
                "type", "upload",
                "unique_filename", true,
                "use_filename", false,
                "overwrite", true));
    }

    /** Uploads a private (authenticated) resume under {@code hireflow/resumes/{userId}}. */
    public StoredFile uploadResume(UUID userId, MultipartFile file) {
        return upload(file, ObjectUtils.asMap(
                "folder", RESUME_FOLDER + "/" + userId,
                "resource_type", "raw",
                "type", "authenticated",
                "unique_filename", true,
                "use_filename", false,
                "overwrite", true));
    }

    private StoredFile upload(MultipartFile file, Map<String, Object> options) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);
            return new StoredFile((String) result.get("secure_url"), (String) result.get("public_id"));
        } catch (IOException e) {
            throw new FileStorageException("Could not read the uploaded file", e);
        } catch (RuntimeException e) {
            throw new FileStorageException("Could not upload the file to storage", e);
        }
    }

    /**
     * Deletes a previously uploaded file. {@code resourceType} is {@code image}
     * or {@code raw}; {@code type} is {@code upload} or {@code authenticated}
     * and must match how the file was uploaded. Throws {@link FileStorageException}
     * on failure so callers can decide whether to fail or log-and-continue.
     */
    public void delete(String publicId, String resourceType, String type) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "type", type,
                    "invalidate", true));
        } catch (Exception e) {
            throw new FileStorageException("Could not delete the file from storage", e);
        }
    }

    /**
     * Builds a signed, secure delivery URL for a private resume. Signing happens
     * server-side with the API secret, which is never exposed to clients — the
     * caller hands the resulting URL to an already-authorized user.
     */
    public String signedResumeUrl(String publicId) {
        return cloudinary.url()
                .resourceType("raw")
                .type("authenticated")
                .secure(true)
                .signed(true)
                .generate(publicId);
    }

    /** The two pieces of Cloudinary state we keep: the delivery URL and the id used to delete. */
    public record StoredFile(String secureUrl, String publicId) {}
}
