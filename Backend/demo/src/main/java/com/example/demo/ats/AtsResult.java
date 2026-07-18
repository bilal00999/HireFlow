package com.example.demo.ats;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ATS scoring output for one application. Mirrors the `ats_results` table in
 * 03-DATABASE-DESIGN.md. The skill arrays use @ElementCollection so the schema
 * is portable across Postgres (prod) and H2 (tests).
 */
@Entity
@Table(name = "ats_results")
@Getter
@Setter
public class AtsResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private int score;                // 0-100 overall

    @Column(name = "skills_match")
    private Integer skillsMatch;

    @Column(name = "experience_match")
    private Integer experienceMatch;

    @Column(name = "education_match")
    private Integer educationMatch;

    @Column(name = "keyword_match")
    private Integer keywordMatch;

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ats_matched_skills", joinColumns = @JoinColumn(name = "ats_result_id"))
    @Column(name = "skill")
    private List<String> matchedSkills = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ats_missing_skills", joinColumns = @JoinColumn(name = "ats_result_id"))
    @Column(name = "skill")
    private List<String> missingSkills = new ArrayList<>();

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt = LocalDateTime.now();
}
