package com.example.demo.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Candidate account. Mirrors the `users` table in 03-DATABASE-DESIGN.md.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // --- Cloudinary profile image (public image; all null until one is uploaded) ---
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "profile_image_public_id", length = 300)
    private String profileImagePublicId;

    @Column(name = "profile_image_name", length = 300)
    private String profileImageName;

    @Column(name = "profile_image_type", length = 100)
    private String profileImageType;

    @Column(name = "profile_image_size")
    private Long profileImageSize;

    @Column(name = "profile_image_updated_at")
    private LocalDateTime profileImageUpdatedAt;

    // --- Cloudinary resume (private raw file; all null until one is uploaded) ---
    @Column(name = "resume_url", length = 500)
    private String resumeUrl;

    @Column(name = "resume_public_id", length = 300)
    private String resumePublicId;

    @Column(name = "resume_name", length = 300)
    private String resumeName;

    @Column(name = "resume_type", length = 150)
    private String resumeType;

    @Column(name = "resume_size")
    private Long resumeSize;

    @Column(name = "resume_updated_at")
    private LocalDateTime resumeUpdatedAt;
}
