package com.example.demo.dashboard;

import com.example.demo.application.ApplicationRepository;
import com.example.demo.application.ApplicationService;
import com.example.demo.application.dto.ApplicantDto;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.dashboard.dto.DashboardStatsDto;
import com.example.demo.dashboard.dto.PipelineDto;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardService}: company-scoped overview stats and
 * the per-job pipeline (stage zero-filling, ownership check, applicant reuse).
 * The authenticated company id is stashed in the security context so
 * {@link SecurityUtils#currentUserId()} resolves during the test.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock JobRepository jobRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationService applicationService;
    @Mock com.example.demo.auth.UserRepository userRepository;
    @Mock com.example.demo.ats.AtsResultRepository atsResultRepository;
    @Mock com.example.demo.assessment.AssessmentAttemptRepository assessmentAttemptRepository;
    @Mock com.example.demo.interview.InterviewSessionRepository interviewSessionRepository;
    @Mock com.example.demo.interview.InterviewMessageRepository interviewMessageRepository;

    DashboardService service;

    UUID companyId;
    UUID jobId;

    @BeforeEach
    void setUp() {
        service = new DashboardService(jobRepository, applicationRepository, applicationService,
                userRepository, atsResultRepository, assessmentAttemptRepository,
                interviewSessionRepository, interviewMessageRepository, new com.fasterxml.jackson.databind.ObjectMapper());
        companyId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        authenticateAs(companyId, "HR");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID principalId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                principalId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Job job(UUID owner) {
        Job j = new Job();
        j.setId(jobId);
        j.setCompanyId(owner);
        j.setTitle("Backend Developer");
        return j;
    }

    @Test
    void overview_aggregatesCompanyScopedCounts() {
        when(jobRepository.countByCompanyIdAndStatus(companyId, "ACTIVE")).thenReturn(3L);
        when(applicationRepository.countByCompanyId(companyId)).thenReturn(142L);
        when(applicationRepository.countByCompanyIdAndStage(companyId, "ASSESSMENT")).thenReturn(24L);
        when(applicationRepository.countByCompanyIdAndStage(companyId, "INTERVIEW")).thenReturn(8L);
        when(applicationRepository.countByCompanyIdAndStage(companyId, "FINAL")).thenReturn(5L);

        DashboardStatsDto stats = service.overview();

        assertThat(stats.activeJobs()).isEqualTo(3);
        assertThat(stats.totalApplications()).isEqualTo(142);
        assertThat(stats.inAssessment()).isEqualTo(24);
        assertThat(stats.inInterview()).isEqualTo(8);
        assertThat(stats.readyForReview()).isEqualTo(5);
    }

    @Test
    void pipeline_zeroFillsAllStages_andMergesCounts() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job(companyId)));
        // Only two stages have applications; the rest must come back as zero.
        when(applicationRepository.countByStageForJob(jobId)).thenReturn(List.of(
                new Object[]{"APPLIED", 80L},
                new Object[]{"ASSESSMENT", 30L}));
        when(applicationService.listForJob(jobId)).thenReturn(List.of());

        PipelineDto dto = service.pipeline(jobId);

        assertThat(dto.jobId()).isEqualTo(jobId.toString());
        assertThat(dto.jobTitle()).isEqualTo("Backend Developer");
        assertThat(dto.stages())
                .containsEntry("APPLIED", 80L)
                .containsEntry("ASSESSMENT", 30L)
                .containsEntry("ATS_REVIEW", 0L)
                .containsEntry("INTERVIEW", 0L)
                .containsEntry("FINAL", 0L)
                .containsEntry("REJECTED", 0L);
        // Every canonical stage present, response shape stable.
        assertThat(dto.stages()).hasSize(6);
    }

    @Test
    void pipeline_includesApplicantList() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job(companyId)));
        when(applicationRepository.countByStageForJob(jobId)).thenReturn(List.of());
        ApplicantDto applicant = new ApplicantDto(
                UUID.randomUUID().toString(), "Casey Candidate", "cand@example.com",
                "/uploads/resumes/cv.pdf", "cover", "INTERVIEW", null, null);
        when(applicationService.listForJob(jobId)).thenReturn(List.of(applicant));

        PipelineDto dto = service.pipeline(jobId);

        assertThat(dto.candidates()).containsExactly(applicant);
    }

    @Test
    void pipeline_missingJob_throwsNotFound() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pipeline(jobId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void pipeline_foreignCompanyJob_rejected() {
        UUID otherCompany = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job(otherCompany)));

        assertThatThrownBy(() -> service.pipeline(jobId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own jobs");
    }

    @Test
    void candidateDetail_ownedApplication_assemblesScorecard() {
        UUID applicationId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        var application = new com.example.demo.application.Application();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setUserId(candidateId);
        application.setStage("FINAL");
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job(companyId)));

        var candidate = new com.example.demo.auth.User();
        candidate.setFullName("Casey Candidate");
        candidate.setEmail("casey@example.com");
        when(userRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        var ats = new com.example.demo.ats.AtsResult();
        ats.setScore(75);
        ats.setPassed(true);
        ats.setAiSummary("Strong match.");
        when(atsResultRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(ats));

        var attempt = new com.example.demo.assessment.AssessmentAttempt();
        attempt.setScore(88);
        when(assessmentAttemptRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.of(attempt));

        var session = new com.example.demo.interview.InterviewSession();
        session.setId(UUID.randomUUID());
        session.setApplicationId(applicationId);
        session.setOverallScore(82);
        session.setPassed(true);
        session.setAiReport("{\"type\":\"INTERVIEW_COMPLETE\",\"overall_score\":82,"
                + "\"recommendation\":\"HIRE\",\"summary\":\"Great.\","
                + "\"strengths\":[\"clear\"],\"weaknesses\":[\"terse\"]}");
        when(interviewSessionRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.of(session));

        var q = new com.example.demo.interview.InterviewMessage();
        q.setRole("ai"); q.setContent("Q1"); q.setOrderIndex(0);
        var a = new com.example.demo.interview.InterviewMessage();
        a.setRole("candidate"); a.setContent("A1"); a.setOrderIndex(1);
        when(interviewMessageRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(q, a));

        var dto = service.candidateDetail(applicationId);

        assertThat(dto.candidateName()).isEqualTo("Casey Candidate");
        assertThat(dto.stage()).isEqualTo("FINAL");
        assertThat(dto.ats().score()).isEqualTo(75);
        assertThat(dto.assessmentScore()).isEqualTo(88);
        assertThat(dto.interview().score()).isEqualTo(82);
        assertThat(dto.interview().recommendation()).isEqualTo("HIRE");
        assertThat(dto.interview().strengths()).containsExactly("clear");
        assertThat(dto.transcript()).hasSize(2);
        assertThat(dto.transcript().get(0).role()).isEqualTo("ai");
    }

    @Test
    void candidateDetail_foreignCompanyJob_rejected() {
        UUID applicationId = UUID.randomUUID();
        var application = new com.example.demo.application.Application();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setUserId(UUID.randomUUID());
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job(UUID.randomUUID())));

        assertThatThrownBy(() -> service.candidateDetail(applicationId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own jobs");
    }
}
