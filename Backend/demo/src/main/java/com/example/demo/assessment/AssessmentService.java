package com.example.demo.assessment;

import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.assessment.dto.*;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.example.demo.token.Token;
import com.example.demo.token.TokenService;
import com.example.demo.token.TokenStatus;
import com.example.demo.token.TokenType;
import com.example.demo.token.TokenValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Candidate-facing assessment flow, all gated by a single-use token rather than
 * a JWT (the candidate follows an emailed link). Three steps:
 *
 * <ol>
 *   <li>{@link #verify} — check the link before rendering the page.</li>
 *   <li>{@link #getQuestions} — load the questions and open an attempt. Does
 *       not consume the token, so a page reload mid-assessment still works.</li>
 *   <li>{@link #submit} — persist answers, consume the token, and hand off to
 *       {@link AssessmentGradingService} for asynchronous grading.</li>
 * </ol>
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final TokenService tokenService;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final AssessmentGradingService gradingService;

    public AssessmentService(TokenService tokenService,
                             ApplicationRepository applicationRepository,
                             JobRepository jobRepository,
                             AssessmentQuestionRepository questionRepository,
                             AssessmentAttemptRepository attemptRepository,
                             AssessmentAnswerRepository answerRepository,
                             AssessmentGradingService gradingService) {
        this.tokenService = tokenService;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.gradingService = gradingService;
    }

    /**
     * Checks a token before the gate screen renders. Never throws for a bad
     * token — an invalid link is an expected outcome, returned as valid=false.
     */
    @Transactional(readOnly = true)
    public VerifyTokenResponse verify(String tokenValue) {
        TokenValidation validation = tokenService.validate(tokenValue, TokenType.ASSESSMENT);
        if (!validation.isValid()) {
            return VerifyTokenResponse.invalid(reasonFor(validation.status()));
        }
        Job job = jobForToken(validation.token());
        long questionCount = questionRepository.countByJobId(job.getId());
        int timeLimit = job.getAssessmentTimeLimit() != null ? job.getAssessmentTimeLimit() : 30;
        return VerifyTokenResponse.valid(job.getTitle(), (int) questionCount, timeLimit);
    }

    /**
     * Returns the questions for this assessment (without answer keys) and opens
     * an attempt if one isn't open yet. Idempotent across reloads.
     */
    @Transactional
    public AssessmentQuestionsResponse getQuestions(String tokenValue) {
        Token token = requireValidToken(tokenValue);
        Job job = jobForToken(token);

        openAttempt(token);

        List<CandidateQuestionDto> questions =
                questionRepository.findByJobIdOrderByOrderIndexAsc(job.getId())
                        .stream().map(CandidateQuestionDto::from).toList();
        int timeLimit = job.getAssessmentTimeLimit() != null ? job.getAssessmentTimeLimit() : 30;
        return new AssessmentQuestionsResponse(job.getTitle(), timeLimit, questions);
    }

    /**
     * Persists the candidate's answers, consumes the token so the link can't be
     * reused, and triggers asynchronous grading. Returns immediately.
     */
    @Transactional
    public SubmitAssessmentResponse submit(String tokenValue, SubmitAssessmentRequest request) {
        Token token = requireValidToken(tokenValue);
        Job job = jobForToken(token);

        AssessmentAttempt attempt = openAttempt(token);

        // Guard against a stray double-submit within the same transaction window.
        if (!answerRepository.findByAttemptId(attempt.getId()).isEmpty()) {
            throw new BadRequestException("This assessment has already been submitted");
        }

        persistAnswers(attempt, job.getId(), request);

        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setTimeTakenSec((int) ChronoUnit.SECONDS.between(
                attempt.getStartedAt(), attempt.getSubmittedAt()));
        attemptRepository.save(attempt);

        // Consume the token: the link is now spent whether or not grading succeeds.
        tokenService.markUsed(token);

        gradingService.gradeAsync(attempt.getId());

        log.info("Assessment submitted for application {} (attempt {})",
                token.getApplicationId(), attempt.getId());
        return SubmitAssessmentResponse.ok();
    }

    // --- helpers ---

    private void persistAnswers(AssessmentAttempt attempt, UUID jobId,
                                SubmitAssessmentRequest request) {
        // Restrict answers to questions that actually belong to this job.
        List<AssessmentQuestion> questions =
                questionRepository.findByJobIdOrderByOrderIndexAsc(jobId);
        for (SubmitAssessmentRequest.AnswerInput input : request.answers()) {
            UUID questionId = parseUuid(input.questionId());
            boolean belongs = questions.stream().anyMatch(q -> q.getId().equals(questionId));
            if (!belongs) {
                throw new BadRequestException(
                        "Answer references a question that is not part of this assessment");
            }
            AssessmentAnswer answer = new AssessmentAnswer();
            answer.setAttemptId(attempt.getId());
            answer.setQuestionId(questionId);
            answer.setSelectedOption(input.selectedOption());
            answer.setAnswerText(input.answerText());
            answerRepository.save(answer);
        }
    }

    /** Finds the open attempt for this token's application, or creates one. */
    private AssessmentAttempt openAttempt(Token token) {
        return attemptRepository.findByApplicationId(token.getApplicationId())
                .orElseGet(() -> {
                    AssessmentAttempt attempt = new AssessmentAttempt();
                    attempt.setApplicationId(token.getApplicationId());
                    attempt.setTokenId(token.getId());
                    attempt.setStartedAt(LocalDateTime.now());
                    return attemptRepository.save(attempt);
                });
    }

    private Token requireValidToken(String tokenValue) {
        TokenValidation validation = tokenService.validate(tokenValue, TokenType.ASSESSMENT);
        if (!validation.isValid()) {
            throw new BadRequestException("Assessment link is " + reasonFor(validation.status()));
        }
        return validation.token();
    }

    private Job jobForToken(Token token) {
        Application application = applicationRepository.findById(token.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid question id: " + value);
        }
    }

    private String reasonFor(TokenStatus status) {
        return switch (status) {
            case EXPIRED -> "TOKEN_EXPIRED";
            case ALREADY_USED -> "TOKEN_USED";
            default -> "TOKEN_NOT_FOUND";
        };
    }
}
