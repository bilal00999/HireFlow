package com.example.demo.dashboard;

import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.application.ApplicationService;
import com.example.demo.application.dto.ApplicantDto;
import com.example.demo.assessment.AssessmentAttempt;
import com.example.demo.assessment.AssessmentAttemptRepository;
import com.example.demo.ats.AtsResult;
import com.example.demo.ats.AtsResultRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.dashboard.dto.CandidateDetailDto;
import com.example.demo.dashboard.dto.DashboardStatsDto;
import com.example.demo.dashboard.dto.PipelineDto;
import com.example.demo.dashboard.dto.TranscriptMessageDto;
import com.example.demo.interview.InterviewMessage;
import com.example.demo.interview.InterviewMessageRepository;
import com.example.demo.interview.InterviewReport;
import com.example.demo.interview.InterviewSession;
import com.example.demo.interview.InterviewSessionRepository;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only aggregate views for the HR dashboard. Everything is scoped to the
 * authenticated HR account's company, so one company can never see another's
 * numbers. Mirrors the endpoints in 04-API-DESIGN.md.
 */
@Service
public class DashboardService {

    /** Canonical pipeline stages, in flow order, for the per-job breakdown. */
    private static final List<String> PIPELINE_STAGES =
            List.of("APPLIED", "ATS_REVIEW", "ASSESSMENT", "INTERVIEW", "FINAL", "REJECTED");

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;
    private final AtsResultRepository atsResultRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(JobRepository jobRepository,
                            ApplicationRepository applicationRepository,
                            ApplicationService applicationService,
                            UserRepository userRepository,
                            AtsResultRepository atsResultRepository,
                            AssessmentAttemptRepository assessmentAttemptRepository,
                            InterviewSessionRepository interviewSessionRepository,
                            InterviewMessageRepository interviewMessageRepository,
                            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
        this.userRepository = userRepository;
        this.atsResultRepository = atsResultRepository;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.interviewMessageRepository = interviewMessageRepository;
        this.objectMapper = objectMapper;
    }

    /** Overview stats for the logged-in HR account's company. */
    @Transactional(readOnly = true)
    public DashboardStatsDto overview() {
        UUID companyId = SecurityUtils.currentUserId();

        long activeJobs = jobRepository.countByCompanyIdAndStatus(companyId, "ACTIVE");
        long total = applicationRepository.countByCompanyId(companyId);
        long inAssessment = applicationRepository.countByCompanyIdAndStage(companyId, "ASSESSMENT");
        long inInterview = applicationRepository.countByCompanyIdAndStage(companyId, "INTERVIEW");
        long readyForReview = applicationRepository.countByCompanyIdAndStage(companyId, "FINAL");

        return new DashboardStatsDto(activeJobs, total, inAssessment, inInterview, readyForReview);
    }

    /**
     * Per-stage counts plus the full applicant list for one job. Reuses
     * {@link ApplicationService#listForJob} so the company-ownership check and
     * applicant mapping live in exactly one place.
     */
    @Transactional(readOnly = true)
    public PipelineDto pipeline(UUID jobId) {
        UUID companyId = SecurityUtils.currentUserId();
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new BadRequestException("You can only view the pipeline for your own jobs");
        }

        // Seed every stage at zero so the response shape is stable, then fill in.
        Map<String, Long> stages = new LinkedHashMap<>();
        for (String stage : PIPELINE_STAGES) {
            stages.put(stage, 0L);
        }
        for (Object[] row : applicationRepository.countByStageForJob(jobId)) {
            String stage = (String) row[0];
            long count = ((Number) row[1]).longValue();
            // Merge unknown/legacy stages in rather than dropping them.
            stages.merge(stage, count, Long::sum);
        }

        List<ApplicantDto> candidates = applicationService.listForJob(jobId);
        return new PipelineDto(job.getId().toString(), job.getTitle(), stages, candidates);
    }

    /**
     * Full scorecard for one candidate: identity, every stage's score, the AI
     * interview report, and the transcript. Scoped to the caller's company — an
     * HR account can only inspect candidates who applied to its own jobs. Any
     * stage the candidate hasn't reached comes back null.
     */
    @Transactional(readOnly = true)
    public CandidateDetailDto candidateDetail(UUID applicationId) {
        UUID companyId = SecurityUtils.currentUserId();

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new BadRequestException("You can only view candidates for your own jobs");
        }

        User candidate = userRepository.findById(application.getUserId()).orElse(null);

        CandidateDetailDto.AtsSection ats = atsResultRepository.findByApplicationId(applicationId)
                .map(this::toAtsSection).orElse(null);

        Integer assessmentScore = assessmentAttemptRepository.findByApplicationId(applicationId)
                .map(AssessmentAttempt::getScore).orElse(null);

        InterviewSession session = interviewSessionRepository.findByApplicationId(applicationId)
                .orElse(null);
        CandidateDetailDto.InterviewSection interview = toInterviewSection(session);
        List<TranscriptMessageDto> transcript = transcriptFor(session);

        return new CandidateDetailDto(
                application.getId().toString(),
                candidate != null ? candidate.getFullName() : "Unknown",
                candidate != null ? candidate.getEmail() : "Unknown",
                job.getId().toString(),
                job.getTitle(),
                application.getStage(),
                application.getRejectionReason(),
                application.getResumeUrl(),
                application.getCoverLetter(),
                ats,
                assessmentScore,
                interview,
                transcript);
    }

    private CandidateDetailDto.AtsSection toAtsSection(AtsResult r) {
        return new CandidateDetailDto.AtsSection(
                r.getScore(), r.isPassed(), r.getAiSummary(),
                r.getMatchedSkills(), r.getMissingSkills());
    }

    /** Parses the stored AI report JSON into the interview section; null-safe. */
    private CandidateDetailDto.InterviewSection toInterviewSection(InterviewSession session) {
        if (session == null) {
            return null;
        }
        InterviewReport report = null;
        if (session.getAiReport() != null && !session.getAiReport().isBlank()) {
            try {
                report = objectMapper.readValue(session.getAiReport(), InterviewReport.class);
            } catch (Exception e) {
                // Stored report unparseable — fall back to score-only.
                report = null;
            }
        }
        return new CandidateDetailDto.InterviewSection(
                session.getOverallScore(),
                session.getPassed(),
                report != null ? report.recommendation() : null,
                report != null ? report.summary() : null,
                report != null ? report.strengths() : null,
                report != null ? report.weaknesses() : null);
    }

    private List<TranscriptMessageDto> transcriptFor(InterviewSession session) {
        if (session == null) {
            return Collections.emptyList();
        }
        List<InterviewMessage> messages =
                interviewMessageRepository.findBySessionIdOrderByOrderIndexAsc(session.getId());
        return messages.stream()
                .map(m -> new TranscriptMessageDto(m.getRole(), m.getContent(), m.getOrderIndex()))
                .toList();
    }
}
