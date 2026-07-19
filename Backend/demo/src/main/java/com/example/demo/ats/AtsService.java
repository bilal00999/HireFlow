package com.example.demo.ats;

import com.example.demo.ai.AiClient;
import com.example.demo.ai.AiUnavailableException;
import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.email.EmailService;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.example.demo.token.Token;
import com.example.demo.token.TokenService;
import com.example.demo.token.TokenType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Runs ATS resume scoring for an application: extract resume text, ask the LLM
 * to score it against the job, persist the result, and advance the application
 * stage. Scoring runs asynchronously so the candidate's apply request returns
 * immediately.
 */
@Service
public class AtsService {

    private static final Logger log = LoggerFactory.getLogger(AtsService.class);

    /** Assessment links stay valid for 24 hours (per 08-EMAIL-SYSTEM.md). */
    private static final Duration ASSESSMENT_TOKEN_TTL = Duration.ofHours(24);

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final AtsResultRepository atsResultRepository;
    private final ResumeExtractor resumeExtractor;
    private final AtsPromptBuilder promptBuilder;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    public AtsService(ApplicationRepository applicationRepository,
                      JobRepository jobRepository,
                      AtsResultRepository atsResultRepository,
                      ResumeExtractor resumeExtractor,
                      AtsPromptBuilder promptBuilder,
                      AiClient aiClient,
                      ObjectMapper objectMapper,
                      UserRepository userRepository,
                      CompanyRepository companyRepository,
                      TokenService tokenService,
                      EmailService emailService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.atsResultRepository = atsResultRepository;
        this.resumeExtractor = resumeExtractor;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
    }

    /**
     * Fire-and-forget entry point called right after a candidate applies.
     * Runs on a separate thread; failures are logged and the application is
     * flagged for manual review rather than propagating to the caller.
     */
    @Async
    public void scoreAsync(UUID applicationId) {
        try {
            score(applicationId);
        } catch (Exception e) {
            log.error("ATS scoring failed for application {}", applicationId, e);
            markManualReview(applicationId);
        }
    }

    /**
     * Scores one application synchronously. Idempotent: if a result already
     * exists it is left untouched. Returns the persisted result.
     */
    @Transactional
    public AtsResult score(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (atsResultRepository.existsByApplicationId(applicationId)) {
            return atsResultRepository.findByApplicationId(applicationId).orElseThrow();
        }

        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        application.setStage("ATS_REVIEW");
        applicationRepository.save(application);

        String resumeText = resumeExtractor.extractText(application.getResumeUrl());
        AtsScore parsed = callAndParse(resumeText, job);

        int threshold = job.getAtsMinScore() != null ? job.getAtsMinScore() : 60;
        boolean passed = parsed.overallScore() >= threshold;

        AtsResult result = new AtsResult();
        result.setApplicationId(applicationId);
        result.setScore(clamp(parsed.overallScore()));
        result.setSkillsMatch(clampNullable(parsed.skillsMatch()));
        result.setExperienceMatch(clampNullable(parsed.experienceMatch()));
        result.setEducationMatch(clampNullable(parsed.educationMatch()));
        result.setKeywordMatch(clampNullable(parsed.keywordMatch()));
        result.setAiSummary(parsed.summary());
        result.setMatchedSkills(parsed.matchedSkills() != null ? parsed.matchedSkills() : List.of());
        result.setMissingSkills(parsed.missingSkills() != null ? parsed.missingSkills() : List.of());
        result.setPassed(passed);
        result.setReviewedAt(LocalDateTime.now());
        result = atsResultRepository.save(result);

        // Advance the pipeline: pass -> ASSESSMENT, fail -> REJECTED (ATS_FAILED).
        if (passed) {
            application.setStage("ASSESSMENT");
        } else {
            application.setStage("REJECTED");
            application.setRejectionReason("ATS_FAILED");
        }
        application.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(application);

        notifyOutcome(application, job, passed);

        log.info("ATS scored application {}: score={} passed={}", applicationId, result.getScore(), passed);
        return result;
    }

    /**
     * Notifies the candidate of the ATS outcome. On a pass, issues a single-use
     * assessment token and emails the link; on a fail, sends a rejection. Email
     * or token failures are logged but never fail scoring — the persisted stage
     * is the source of truth and the link can be re-issued.
     */
    private void notifyOutcome(Application application, Job job, boolean passed) {
        try {
            User candidate = userRepository.findById(application.getUserId()).orElse(null);
            if (candidate == null) {
                log.warn("No candidate found for application {}; skipping ATS email",
                        application.getId());
                return;
            }
            if (passed) {
                Token token = tokenService.create(
                        application.getId(), TokenType.ASSESSMENT, ASSESSMENT_TOKEN_TTL);
                int timeLimit = job.getAssessmentTimeLimit() != null
                        ? job.getAssessmentTimeLimit() : 30;
                emailService.sendAssessmentInvite(
                        candidate.getEmail(), candidate.getFullName(), job.getTitle(),
                        companyName(job.getCompanyId()), token.getTokenValue(),
                        token.getExpiresAt(), timeLimit);
            } else {
                emailService.sendAtsRejection(
                        candidate.getEmail(), candidate.getFullName(), job.getTitle());
            }
        } catch (Exception e) {
            log.warn("Failed to send ATS outcome notification for application {}",
                    application.getId(), e);
        }
    }

    private String companyName(UUID companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse("the company");
    }

    private AtsScore callAndParse(String resumeText, Job job) {
        String raw = aiClient.complete(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(resumeText, job));
        try {
            return objectMapper.readValue(stripToJson(raw), AtsScore.class);
        } catch (Exception e) {
            throw new AiUnavailableException("Could not parse ATS response as JSON", e);
        }
    }

    /**
     * LLMs frequently wrap JSON in ```json ... ``` fences or add prose around
     * it. Extract the first {...} object so parsing is resilient to that.
     */
    private String stripToJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    /**
     * Reads the ATS result for an application on behalf of an HR user,
     * enforcing that the caller's company owns the underlying job.
     */
    @Transactional(readOnly = true)
    public AtsResult getForHr(UUID applicationId, UUID companyId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new com.example.demo.common.BadRequestException(
                    "You can only view ATS results for your own jobs");
        }
        return atsResultRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ATS result not available yet for this application"));
    }

    private void markManualReview(UUID applicationId) {
        applicationRepository.findById(applicationId).ifPresent(app -> {
            app.setStage("MANUAL_REVIEW");
            app.setUpdatedAt(LocalDateTime.now());
            applicationRepository.save(app);
        });
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private Integer clampNullable(Integer value) {
        return value == null ? null : clamp(value);
    }
}
