package com.example.demo.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Job posting created by a company (HR). Mirrors the `jobs` table in
 * 03-DATABASE-DESIGN.md. Pipeline-config fields (ATS threshold, assessment,
 * interview settings) are internal and never exposed on the public detail view.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String requirements;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_required_skills", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "skill")
    private List<String> requiredSkills = new ArrayList<>();

    @Column(name = "job_type", length = 50)
    private String jobType;      // FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT

    @Column(length = 200)
    private String location;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(length = 10)
    private String currency = "USD";

    // --- Pipeline configuration (internal) ---
    @Column(name = "ats_min_score")
    private Integer atsMinScore = 60;

    @Column(name = "assessment_pass_score")
    private Integer assessmentPassScore = 70;

    @Column(name = "assessment_time_limit")
    private Integer assessmentTimeLimit = 30;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_interview_topics", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "topic")
    private List<String> interviewTopics = new ArrayList<>();

    @Column(name = "interview_duration")
    private Integer interviewDuration = 20;

    @Column(name = "interview_num_questions")
    private Integer interviewNumQuestions = 5;

    // --- Status ---
    @Column(length = 20)
    private String status = "DRAFT";   // DRAFT, ACTIVE, CLOSED

    private LocalDate deadline;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
