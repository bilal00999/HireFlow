package com.example.demo.token;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A secure, expiring, single-use token that gates access to the assessment and
 * interview stages. Mirrors the `tokens` table in 03-DATABASE-DESIGN.md.
 */
@Entity
@Table(name = "tokens")
@Getter
@Setter
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The opaque, URL-safe secret handed to the candidate in an email link. */
    @Column(name = "token_value", nullable = false, unique = true, length = 255)
    private String tokenValue;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    /** ASSESSMENT or INTERVIEW. */
    @Column(name = "token_type", nullable = false, length = 30)
    private String tokenType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** NULL until the token is consumed; set once so a link works only once. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
