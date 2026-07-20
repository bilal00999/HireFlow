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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public DashboardService(JobRepository jobRepository,
                            ApplicationRepository applicationRepository,
                            ApplicationService applicationService) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
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
}
