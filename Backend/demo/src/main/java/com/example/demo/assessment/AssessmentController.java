package com.example.demo.assessment;

import com.example.demo.assessment.dto.AssessmentQuestionsResponse;
import com.example.demo.assessment.dto.SubmitAssessmentRequest;
import com.example.demo.assessment.dto.SubmitAssessmentResponse;
import com.example.demo.assessment.dto.VerifyTokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Candidate-facing assessment endpoints. These are token-gated rather than
 * JWT-authenticated (the candidate arrives via an emailed link), so the path is
 * whitelisted in {@code SecurityConfig} and every method validates the token
 * through {@link AssessmentService}.
 */
@RestController
@RequestMapping("/api/v1/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    // --- Verify a link before rendering the gate screen ---
    @GetMapping("/verify/{token}")
    public VerifyTokenResponse verify(@PathVariable String token) {
        return assessmentService.verify(token);
    }

    // --- Load questions and open the attempt ---
    @GetMapping("/{token}/questions")
    public AssessmentQuestionsResponse questions(@PathVariable String token) {
        return assessmentService.getQuestions(token);
    }

    // --- Submit answers (grading runs asynchronously) ---
    @PostMapping("/{token}/submit")
    public SubmitAssessmentResponse submit(@PathVariable String token,
                                           @Valid @RequestBody SubmitAssessmentRequest request) {
        return assessmentService.submit(token, request);
    }
}
