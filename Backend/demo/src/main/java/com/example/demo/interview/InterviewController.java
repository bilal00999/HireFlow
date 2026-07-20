package com.example.demo.interview;

import com.example.demo.interview.dto.VerifyInterviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Candidate-facing REST surface for the interview. Only the pre-flight token
 * check lives here; the interview itself runs over the WebSocket at
 * {@code /ws/interview}. Path is whitelisted in {@code SecurityConfig} and the
 * token is validated inside {@link InterviewService}.
 */
@RestController
@RequestMapping("/api/v1/interview")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    // --- Verify a link before opening the WebSocket ---
    @GetMapping("/verify/{token}")
    public VerifyInterviewResponse verify(@PathVariable String token) {
        return interviewService.verify(token);
    }
}
