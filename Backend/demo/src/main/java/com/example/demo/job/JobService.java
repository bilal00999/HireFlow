package com.example.demo.job;

import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.job.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class JobService {

    private static final Set<String> VALID_STATUSES = Set.of("DRAFT", "ACTIVE", "CLOSED");

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public JobService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    // --- HR: create ---
    public JobDetailDto createJob(CreateJobRequest req) {
        UUID companyId = SecurityUtils.currentUserId();
        Job job = new Job();
        job.setCompanyId(companyId);
        applyRequest(job, req);
        job = jobRepository.save(job);
        return toDetail(job);
    }

    // --- Public: paginated search ---
    public Page<JobSummaryDto> search(String keyword, String type, String location, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Job> jobs = jobRepository.search(
                blankToNull(keyword), blankToNull(type), blankToNull(location), pageable);
        return jobs.map(this::toSummary);
    }

    // --- Public: detail ---
    public JobDetailDto getById(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        return toDetail(job);
    }

    // --- HR: list own jobs ---
    public List<JobSummaryDto> listMyJobs() {
        UUID companyId = SecurityUtils.currentUserId();
        return jobRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(this::toSummary).toList();
    }

    // --- HR: update status ---
    public JobDetailDto updateStatus(UUID id, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new BadRequestException("Status must be one of DRAFT, ACTIVE, CLOSED");
        }
        Job job = requireOwnedJob(id);
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        return toDetail(jobRepository.save(job));
    }

    // --- HR: update details ---
    public JobDetailDto updateJob(UUID id, CreateJobRequest req) {
        Job job = requireOwnedJob(id);
        applyRequest(job, req);
        job.setUpdatedAt(LocalDateTime.now());
        return toDetail(jobRepository.save(job));
    }

    // Ensures the job exists AND belongs to the calling HR account.
    private Job requireOwnedJob(UUID id) {
        UUID companyId = SecurityUtils.currentUserId();
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new BadRequestException("You can only modify your own jobs");
        }
        return job;
    }

    private void applyRequest(Job job, CreateJobRequest req) {
        job.setTitle(req.title());
        job.setDescription(req.description());
        job.setRequirements(req.requirements());
        if (req.requiredSkills() != null) job.setRequiredSkills(req.requiredSkills());
        if (req.jobType() != null) job.setJobType(req.jobType());
        job.setLocation(req.location());
        job.setSalaryMin(req.salaryMin());
        job.setSalaryMax(req.salaryMax());
        if (req.atsMinScore() != null) job.setAtsMinScore(req.atsMinScore());
        if (req.assessmentPassScore() != null) job.setAssessmentPassScore(req.assessmentPassScore());
        if (req.assessmentTimeLimit() != null) job.setAssessmentTimeLimit(req.assessmentTimeLimit());
        if (req.interviewTopics() != null) job.setInterviewTopics(req.interviewTopics());
        if (req.interviewDuration() != null) job.setInterviewDuration(req.interviewDuration());
        if (req.interviewNumQuestions() != null) job.setInterviewNumQuestions(req.interviewNumQuestions());
        job.setDeadline(req.deadline());
    }

    private String companyName(UUID companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse("Unknown");
    }

    private JobSummaryDto toSummary(Job j) {
        return new JobSummaryDto(
                j.getId().toString(), j.getTitle(), companyName(j.getCompanyId()),
                j.getJobType(), j.getLocation(), j.getSalaryMin(), j.getSalaryMax(),
                j.getCurrency(), j.getDeadline(), j.getStatus());
    }

    private JobDetailDto toDetail(Job j) {
        return new JobDetailDto(
                j.getId().toString(), j.getTitle(), companyName(j.getCompanyId()),
                j.getDescription(), j.getRequirements(), j.getRequiredSkills(),
                j.getJobType(), j.getLocation(), j.getSalaryMin(), j.getSalaryMax(),
                j.getCurrency(), j.getDeadline(), j.getStatus());
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
