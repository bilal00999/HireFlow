package com.example.demo.job;

import com.example.demo.assessment.AssessmentQuestionService;
import com.example.demo.assessment.dto.AddQuestionsRequest;
import com.example.demo.assessment.dto.QuestionDto;
import com.example.demo.job.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final AssessmentQuestionService questionService;

    public JobController(JobService jobService, AssessmentQuestionService questionService) {
        this.jobService = jobService;
        this.questionService = questionService;
    }

    // --- Public: browse active jobs with filters ---
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<JobSummaryDto> result = jobService.search(keyword, type, location, page, size);
        return Map.of(
                "content", result.getContent(),
                "totalPages", result.getTotalPages(),
                "totalElements", result.getTotalElements(),
                "page", result.getNumber());
    }

    // --- HR: list own jobs (must come before /{id} to avoid path clash) ---
    @GetMapping("/mine")
    @PreAuthorize("hasRole('HR')")
    public List<JobSummaryDto> myJobs() {
        return jobService.listMyJobs();
    }

    // --- Public: single job detail ---
    @GetMapping("/{id}")
    public JobDetailDto getById(@PathVariable UUID id) {
        return jobService.getById(id);
    }

    // --- HR: create a job ---
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HR')")
    public JobDetailDto create(@Valid @RequestBody CreateJobRequest req) {
        return jobService.createJob(req);
    }

    // --- HR: update job details ---
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public JobDetailDto update(@PathVariable UUID id, @Valid @RequestBody CreateJobRequest req) {
        return jobService.updateJob(id, req);
    }

    // --- HR: publish / unpublish / close ---
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('HR')")
    public JobDetailDto updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest req) {
        return jobService.updateStatus(id, req.status());
    }

    // --- HR: append assessment questions to a job ---
    @PostMapping("/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HR')")
    public List<QuestionDto> addQuestions(@PathVariable UUID id,
                                          @Valid @RequestBody AddQuestionsRequest req) {
        return questionService.addQuestions(id, req);
    }

    // --- HR: list a job's assessment questions ---
    @GetMapping("/{id}/questions")
    @PreAuthorize("hasRole('HR')")
    public List<QuestionDto> getQuestions(@PathVariable UUID id) {
        return questionService.listQuestions(id);
    }
}
