package com.example.demo.assessment;

import com.example.demo.ai.AiClient;
import com.example.demo.ai.AiUnavailableException;
import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.email.EmailService;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.example.demo.token.Token;
import com.example.demo.token.TokenService;
import com.example.demo.token.TokenType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grades a submitted assessment attempt and advances the pipeline. MCQ answers
 * are auto-graded against the stored correct option; TEXT answers are scored by
 * the LLM. The normalized 0-100 score is compared to the job's pass mark:
 *
 * <ul>
 *   <li>pass — stage moves to {@code INTERVIEW}, an interview token is issued,
 *       and the interview invite (email #6) goes out.</li>
 *   <li>fail — stage moves to {@code REJECTED} with reason {@code ASSESSMENT_FAILED}
 *       and the rejection notice (email #5) goes out.</li>
 * </ul>
 *
 * <p>Lives in its own bean so {@link AssessmentService#submit} can invoke
 * {@link #gradeAsync} across a proxy boundary (Spring {@code @Async} is a no-op
 * on self-invocation), mirroring how apply → {@code AtsService.scoreAsync} works.
 */
@Service
public class AssessmentGradingService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentGradingService.class);

    /** Interview links stay valid for 24 hours, like assessment links. */
    private static final Duration INTERVIEW_TOKEN_TTL = Duration.ofHours(24);

    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AssessmentGradingPromptBuilder promptBuilder;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final EmailService emailService;

    public AssessmentGradingService(AssessmentAttemptRepository attemptRepository,
                                    AssessmentAnswerRepository answerRepository,
                                    AssessmentQuestionRepository questionRepository,
                                    ApplicationRepository applicationRepository,
                                    JobRepository jobRepository,
                                    UserRepository userRepository,
                                    CompanyRepository companyRepository,
                                    AssessmentGradingPromptBuilder promptBuilder,
                                    AiClient aiClient,
                                    ObjectMapper objectMapper,
                                    TokenService tokenService,
                                    EmailService emailService) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.emailService = emailService;
    }

    /**
     * Fire-and-forget entry point called right after submit. Runs on a separate
     * thread; failures are logged and the attempt left ungraded rather than
     * propagating (the candidate has already been told results come by email).
     */
    @Async
    public void gradeAsync(UUID attemptId) {
        try {
            grade(attemptId);
        } catch (Exception e) {
            log.error("Assessment grading failed for attempt {}", attemptId, e);
        }
    }

    /**
     * Grades one attempt synchronously. Idempotent: if the attempt is already
     * scored it is left untouched.
     */
    @Transactional
    public void grade(UUID attemptId) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        if (attempt.getScore() != null) {
            return; // already graded
        }

        Application application = applicationRepository.findById(attempt.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        Map<UUID, AssessmentQuestion> questions = new HashMap<>();
        for (AssessmentQuestion q : questionRepository.findByJobIdOrderByOrderIndexAsc(job.getId())) {
            questions.put(q.getId(), q);
        }

        int earned = 0;
        int possible = 0;
        for (AssessmentQuestion q : questions.values()) {
            possible += maxScore(q);
        }

        List<AssessmentAnswer> answers = answerRepository.findByAttemptId(attemptId);
        for (AssessmentAnswer answer : answers) {
            AssessmentQuestion question = questions.get(answer.getQuestionId());
            if (question == null) {
                continue; // answer to a question no longer on the job; ignore
            }
            earned += "MCQ".equals(question.getQuestionType())
                    ? gradeMcq(question, answer)
                    : gradeText(job, question, answer);
        }

        // Normalize to 0-100. A job with no questions (or zero total points)
        // can't be failed on content, so treat it as a pass.
        int score = possible > 0 ? (int) Math.round((earned * 100.0) / possible) : 100;
        int passMark = job.getAssessmentPassScore() != null ? job.getAssessmentPassScore() : 70;
        boolean passed = score >= passMark;

        attempt.setScore(score);
        attempt.setPassed(passed);
        attemptRepository.save(attempt);

        advancePipeline(application, job, passed);

        log.info("Graded attempt {}: score={} passed={} (earned {}/{})",
                attemptId, score, passed, earned, possible);
    }

    /** MCQ: full marks for the correct option, zero otherwise. */
    private int gradeMcq(AssessmentQuestion question, AssessmentAnswer answer) {
        boolean correct = answer.getSelectedOption() != null
                && answer.getSelectedOption().equals(question.getCorrectOption());
        answer.setIsCorrect(correct);
        int awarded = correct ? maxScore(question) : 0;
        answerRepository.save(answer);
        return awarded;
    }

    /**
     * TEXT: ask the LLM to score the answer 0..maxScore. If the model is
     * unavailable or returns unparseable output, award zero and record that in
     * the feedback rather than failing the whole grading run.
     */
    private int gradeText(Job job, AssessmentQuestion question, AssessmentAnswer answer) {
        int max = maxScore(question);
        try {
            String raw = aiClient.complete(
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(job, question, answer.getAnswerText()));
            TextGrade grade = objectMapper.readValue(stripToJson(raw), TextGrade.class);
            int awarded = Math.max(0, Math.min(max, grade.score()));
            answer.setAiScore(awarded);
            answer.setAiFeedback(grade.feedback());
            answerRepository.save(answer);
            return awarded;
        } catch (Exception e) {
            log.warn("AI grading failed for answer {} (question {}); awarding 0",
                    answer.getId(), question.getId(), e);
            answer.setAiScore(0);
            answer.setAiFeedback("Could not be graded automatically.");
            answerRepository.save(answer);
            return 0;
        }
    }

    /**
     * Advances the pipeline after grading. On a pass, issues a single-use
     * interview token and emails the link; on a fail, sends a rejection.
     * Notification failures are logged but never roll back the graded stage.
     */
    private void advancePipeline(Application application, Job job, boolean passed) {
        if (passed) {
            application.setStage("INTERVIEW");
        } else {
            application.setStage("REJECTED");
            application.setRejectionReason("ASSESSMENT_FAILED");
        }
        application.setUpdatedAt(java.time.LocalDateTime.now());
        applicationRepository.save(application);

        try {
            User candidate = userRepository.findById(application.getUserId()).orElse(null);
            if (candidate == null) {
                log.warn("No candidate for application {}; skipping assessment email",
                        application.getId());
                return;
            }
            if (passed) {
                Token token = tokenService.create(
                        application.getId(), TokenType.INTERVIEW, INTERVIEW_TOKEN_TTL);
                int duration = job.getInterviewDuration() != null ? job.getInterviewDuration() : 20;
                emailService.sendInterviewInvite(
                        candidate.getEmail(), candidate.getFullName(), job.getTitle(),
                        companyName(job.getCompanyId()), token.getTokenValue(),
                        token.getExpiresAt(), duration);
            } else {
                emailService.sendAssessmentRejection(
                        candidate.getEmail(), candidate.getFullName(), job.getTitle());
            }
        } catch (Exception e) {
            log.warn("Failed to send assessment outcome notification for application {}",
                    application.getId(), e);
        }
    }

    private int maxScore(AssessmentQuestion q) {
        return q.getMaxScore() != null ? q.getMaxScore() : 10;
    }

    private String companyName(UUID companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse("the company");
    }

    /**
     * LLMs often wrap JSON in ```json ... ``` fences or add prose; extract the
     * first {...} object so parsing is resilient to that. Mirrors AtsService.
     */
    private String stripToJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
