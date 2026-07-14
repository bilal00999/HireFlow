package com.example.demo.application;

import com.example.demo.application.dto.*;
import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ResumeStorageService resumeStorage;

    public ApplicationService(ApplicationRepository applicationRepository,
                              JobRepository jobRepository,
                              UserRepository userRepository,
                              CompanyRepository companyRepository,
                              ResumeStorageService resumeStorage) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.resumeStorage = resumeStorage;
    }

    // --- Candidate: apply to a job ---
    public ApplyResponse apply(UUID jobId, String coverLetter, MultipartFile resume) {
        UUID userId = SecurityUtils.currentUserId();

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!"ACTIVE".equals(job.getStatus())) {
            throw new BadRequestException("This job is not accepting applications");
        }
        if (applicationRepository.existsByJobIdAndUserId(jobId, userId)) {
            throw new BadRequestException("You have already applied to this job");
        }

        // Store the resume first so we never persist an application without one.
        String resumeUrl = resumeStorage.store(resume);

        Application application = new Application();
        application.setJobId(jobId);
        application.setUserId(userId);
        application.setResumeUrl(resumeUrl);
        application.setCoverLetter(coverLetter);
        application = applicationRepository.save(application);

        return new ApplyResponse(
                application.getId().toString(),
                application.getStage(),
                "Application received. You will be notified.");
    }

    // --- Candidate: my applications ---
    public List<MyApplicationDto> listMine() {
        UUID userId = SecurityUtils.currentUserId();
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toMyApplication).toList();
    }

    // --- HR: applicants for one of my jobs ---
    public List<ApplicantDto> listForJob(UUID jobId) {
        Job job = requireOwnedJob(jobId);
        return applicationRepository.findByJobIdOrderByCreatedAtDesc(job.getId())
                .stream().map(this::toApplicant).toList();
    }

    // Ensures the job exists AND belongs to the calling HR account.
    private Job requireOwnedJob(UUID jobId) {
        UUID companyId = SecurityUtils.currentUserId();
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new BadRequestException("You can only view applicants for your own jobs");
        }
        return job;
    }

    private MyApplicationDto toMyApplication(Application a) {
        Job job = jobRepository.findById(a.getJobId()).orElse(null);
        var jobRef = new MyApplicationDto.JobRef(
                a.getJobId().toString(),
                job != null ? job.getTitle() : "Unknown",
                job != null ? companyName(job.getCompanyId()) : "Unknown");
        return new MyApplicationDto(
                a.getId().toString(), jobRef, a.getStage(),
                a.getRejectionReason(), a.getCreatedAt());
    }

    private ApplicantDto toApplicant(Application a) {
        User candidate = userRepository.findById(a.getUserId()).orElse(null);
        return new ApplicantDto(
                a.getId().toString(),
                candidate != null ? candidate.getFullName() : "Unknown",
                candidate != null ? candidate.getEmail() : "Unknown",
                a.getResumeUrl(),
                a.getCoverLetter(),
                a.getStage(),
                a.getRejectionReason(),
                a.getCreatedAt());
    }

    private String companyName(UUID companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse("Unknown");
    }
}
