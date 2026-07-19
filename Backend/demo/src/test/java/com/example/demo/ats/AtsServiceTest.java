package com.example.demo.ats;

import com.example.demo.ai.AiClient;
import com.example.demo.ai.AiUnavailableException;
import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AtsService} with all collaborators mocked. Covers the
 * pass/fail stage transitions, idempotency, AI-failure fallback, and the HR
 * ownership check.
 */
@ExtendWith(MockitoExtension.class)
class AtsServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock JobRepository jobRepository;
    @Mock AtsResultRepository atsResultRepository;
    @Mock ResumeExtractor resumeExtractor;
    @Mock AiClient aiClient;
    @Mock com.example.demo.auth.UserRepository userRepository;
    @Mock com.example.demo.auth.CompanyRepository companyRepository;
    @Mock com.example.demo.token.TokenService tokenService;
    @Mock com.example.demo.email.EmailService emailService;

    // Real prompt builder and object mapper — cheap, deterministic, no need to mock.
    AtsPromptBuilder promptBuilder = new AtsPromptBuilder();
    ObjectMapper objectMapper = new ObjectMapper();

    AtsService atsService;

    UUID applicationId;
    UUID jobId;
    Application application;
    Job job;

    @BeforeEach
    void setUp() {
        atsService = new AtsService(applicationRepository, jobRepository, atsResultRepository,
                resumeExtractor, promptBuilder, aiClient, objectMapper,
                userRepository, companyRepository, tokenService, emailService);

        applicationId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        application = new Application();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setResumeUrl("/uploads/resumes/cv.pdf");
        application.setStage("APPLIED");

        job = new Job();
        job.setId(jobId);
        job.setCompanyId(UUID.randomUUID());
        job.setTitle("Backend Developer");
        job.setDescription("Build APIs");
        job.setRequiredSkills(List.of("Java", "Spring Boot"));
        job.setAtsMinScore(60);
    }

    private void stubHappyPath(String aiJson) {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(atsResultRepository.existsByApplicationId(applicationId)).thenReturn(false);
        when(resumeExtractor.extractText(application.getResumeUrl())).thenReturn("resume text");
        when(aiClient.complete(anyString(), anyString())).thenReturn(aiJson);
        when(atsResultRepository.save(any(AtsResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // The notification path (runs for every scored application) looks up the
        // candidate; stub it lenient so tests that only assert scoring still pass.
        com.example.demo.auth.User candidate = new com.example.demo.auth.User();
        candidate.setEmail("cand@example.com");
        candidate.setFullName("Cand Idate");
        lenient().when(userRepository.findById(application.getUserId()))
                .thenReturn(Optional.of(candidate));
    }

    @Test
    void score_passing_advancesToAssessment_issuesTokenAndInvite() {
        stubHappyPath("""
                {"overall_score": 85, "skills_match": 90, "experience_match": 80,
                 "education_match": 70, "keyword_match": 88,
                 "matched_skills": ["Java","Spring Boot"], "missing_skills": [],
                 "summary": "Strong match."}
                """);
        com.example.demo.token.Token token = new com.example.demo.token.Token();
        token.setTokenValue("tok-123");
        token.setExpiresAt(java.time.LocalDateTime.now().plusHours(24));
        when(tokenService.create(eq(applicationId),
                eq(com.example.demo.token.TokenType.ASSESSMENT), any()))
                .thenReturn(token);
        when(companyRepository.findById(job.getCompanyId())).thenReturn(Optional.empty());

        AtsResult result = atsService.score(applicationId);

        assertThat(result.getScore()).isEqualTo(85);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getMatchedSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.getAiSummary()).isEqualTo("Strong match.");
        assertThat(application.getStage()).isEqualTo("ASSESSMENT");
        assertThat(application.getRejectionReason()).isNull();

        // A single-use assessment token is issued and the invite carries its value.
        verify(tokenService).create(eq(applicationId),
                eq(com.example.demo.token.TokenType.ASSESSMENT), any());
        verify(emailService).sendAssessmentInvite(eq("cand@example.com"), eq("Cand Idate"),
                eq("Backend Developer"), anyString(), eq("tok-123"), any(), anyInt());
        verify(emailService, never()).sendAtsRejection(anyString(), anyString(), anyString());
    }

    @Test
    void score_belowThreshold_rejects_sendsRejectionNoToken() {
        stubHappyPath("""
                {"overall_score": 40, "skills_match": 30, "matched_skills": ["Java"],
                 "missing_skills": ["Spring Boot"], "summary": "Weak match."}
                """);

        AtsResult result = atsService.score(applicationId);

        assertThat(result.isPassed()).isFalse();
        assertThat(application.getStage()).isEqualTo("REJECTED");
        assertThat(application.getRejectionReason()).isEqualTo("ATS_FAILED");

        verify(emailService).sendAtsRejection("cand@example.com", "Cand Idate", "Backend Developer");
        verify(tokenService, never()).create(any(), any(), any());
        verify(emailService, never()).sendAssessmentInvite(anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyInt());
    }

    @Test
    void score_stripsMarkdownFencesAroundJson() {
        stubHappyPath("""
                Here is the result:
                ```json
                {"overall_score": 75, "summary": "ok"}
                ```
                """);

        AtsResult result = atsService.score(applicationId);

        assertThat(result.getScore()).isEqualTo(75);
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void score_clampsOutOfRangeScores() {
        stubHappyPath("""
                {"overall_score": 140, "skills_match": -20, "summary": "weird"}
                """);

        AtsResult result = atsService.score(applicationId);

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getSkillsMatch()).isEqualTo(0);
    }

    @Test
    void score_whenResultExists_isIdempotent() {
        AtsResult existing = new AtsResult();
        existing.setApplicationId(applicationId);
        existing.setScore(99);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(atsResultRepository.existsByApplicationId(applicationId)).thenReturn(true);
        when(atsResultRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(existing));

        AtsResult result = atsService.score(applicationId);

        assertThat(result.getScore()).isEqualTo(99);
        verify(aiClient, never()).complete(anyString(), anyString());
        verify(atsResultRepository, never()).save(any());
    }

    @Test
    void score_invalidJson_throwsAiUnavailable() {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(atsResultRepository.existsByApplicationId(applicationId)).thenReturn(false);
        when(resumeExtractor.extractText(anyString())).thenReturn("resume text");
        when(aiClient.complete(anyString(), anyString())).thenReturn("not json at all");

        assertThatThrownBy(() -> atsService.score(applicationId))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void scoreAsync_onFailure_marksManualReview() {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(atsResultRepository.existsByApplicationId(applicationId)).thenReturn(false);
        when(resumeExtractor.extractText(anyString()))
                .thenThrow(new ResumeExtractionException("bad file"));

        atsService.scoreAsync(applicationId);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(application.getStage()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void getForHr_wrongCompany_isRejected() {
        UUID otherCompany = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> atsService.getForHr(applicationId, otherCompany))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getForHr_ownerButNoResultYet_throwsNotFound() {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(atsResultRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atsService.getForHr(applicationId, job.getCompanyId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getForHr_owner_returnsResult() {
        AtsResult existing = new AtsResult();
        existing.setApplicationId(applicationId);
        existing.setScore(80);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(atsResultRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(existing));

        AtsResult result = atsService.getForHr(applicationId, job.getCompanyId());

        assertThat(result.getScore()).isEqualTo(80);
    }
}
