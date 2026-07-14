package com.example.demo.application;

import com.example.demo.application.dto.ApplicantDto;
import com.example.demo.application.dto.ApplyResponse;
import com.example.demo.application.dto.MyApplicationDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // --- Candidate: apply for a job (multipart: resume file + fields) ---
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplyResponse apply(
            @RequestParam("jobId") UUID jobId,
            @RequestParam(value = "coverLetter", required = false) String coverLetter,
            @RequestParam("resume") MultipartFile resume) {
        return applicationService.apply(jobId, coverLetter, resume);
    }

    // --- Candidate: list my applications with their stages ---
    @GetMapping("/my")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<MyApplicationDto> myApplications() {
        return applicationService.listMine();
    }

    // --- HR: list all applicants for one of my jobs ---
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('HR')")
    public List<ApplicantDto> applicantsForJob(@PathVariable UUID jobId) {
        return applicationService.listForJob(jobId);
    }
}
