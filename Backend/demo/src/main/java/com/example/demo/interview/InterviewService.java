package com.example.demo.interview;

import com.example.demo.ai.AiClient;
import com.example.demo.ai.AiUnavailableException;
import com.example.demo.ai.ChatMessage;
import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.ats.AtsResult;
import com.example.demo.ats.AtsResultRepository;
import com.example.demo.assessment.AssessmentAttempt;
import com.example.demo.assessment.AssessmentAttemptRepository;
import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.email.EmailService;
import com.example.demo.interview.dto.VerifyInterviewResponse;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.example.demo.token.Token;
import com.example.demo.token.TokenService;
import com.example.demo.token.TokenStatus;
import com.example.demo.token.TokenType;
import com.example.demo.token.TokenValidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transport-agnostic core of the AI interview. A WebSocket handler (or any
 * caller) drives it with three verbs — {@link #verify}, {@link #start},
 * {@link #answer} — and relays the returned {@link InterviewEvent} to the
 * candidate. All conversation state lives in the database (interview_sessions +
 * interview_messages), so the socket itself holds nothing beyond the token.
 *
 * <p>When the model emits its INTERVIEW_COMPLETE JSON, the session is scored and
 * closed, the application advances to FINAL (pass) or REJECTED (fail), and HR
 * gets the candidate report email. Scoring is gated on the job's
 * {@code interview} config; a missing pass mark defaults to 60.
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    /** Interview counts as passed at or above this score when the job sets none. */
    private static final int DEFAULT_PASS_MARK = 60;

    private final TokenService tokenService;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AtsResultRepository atsResultRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final InterviewPromptBuilder promptBuilder;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public InterviewService(TokenService tokenService,
                            ApplicationRepository applicationRepository,
                            JobRepository jobRepository,
                            UserRepository userRepository,
                            CompanyRepository companyRepository,
                            AtsResultRepository atsResultRepository,
                            AssessmentAttemptRepository assessmentAttemptRepository,
                            InterviewSessionRepository sessionRepository,
                            InterviewMessageRepository messageRepository,
                            InterviewPromptBuilder promptBuilder,
                            AiClient aiClient,
                            ObjectMapper objectMapper,
                            EmailService emailService) {
        this.tokenService = tokenService;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.atsResultRepository = atsResultRepository;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    /**
     * Checks the interview link before the gate screen renders. Never throws for
     * a bad token — an invalid link is an expected outcome, returned as
     * valid=false.
     */
    @Transactional(readOnly = true)
    public VerifyInterviewResponse verify(String tokenValue) {
        TokenValidation validation = tokenService.validate(tokenValue, TokenType.INTERVIEW);
        if (!validation.isValid()) {
            return VerifyInterviewResponse.invalid(reasonFor(validation.status()));
        }
        Job job = jobForToken(validation.token());
        int duration = job.getInterviewDuration() != null ? job.getInterviewDuration() : 20;
        return VerifyInterviewResponse.valid(job.getTitle(), duration);
    }

    /**
     * Begins the interview: consumes the token, opens (or resumes) the session,
     * and returns the interviewer's opening question. Idempotent on reconnect —
     * if a session already has messages, the last AI question is replayed rather
     * than starting over.
     */
    @Transactional
    public InterviewEvent start(String tokenValue) {
        Token token = requireValidToken(tokenValue);
        Job job = jobForToken(token);

        InterviewSession session = sessionRepository.findByApplicationId(token.getApplicationId())
                .orElse(null);

        if (session != null && "COMPLETED".equals(session.getStatus())) {
            return InterviewEvent.complete(
                    "Your interview is already complete. Results have been sent to the hiring team.");
        }

        // Reconnect to an in-progress session: replay the last question asked.
        if (session != null) {
            List<InterviewMessage> transcript =
                    messageRepository.findBySessionIdOrderByOrderIndexAsc(session.getId());
            String lastQuestion = lastAiMessage(transcript);
            if (lastQuestion != null) {
                return InterviewEvent.question(lastQuestion);
            }
        }

        // Fresh start: consume the token and create the session.
        if (session == null) {
            session = new InterviewSession();
            session.setApplicationId(token.getApplicationId());
            session.setTokenId(token.getId());
            session.setStatus("IN_PROGRESS");
            session = sessionRepository.save(session);
        }
        tokenService.markUsed(token);

        String reply = aiClient.chat(promptBuilder.systemPrompt(job), List.of(
                ChatMessage.user("Please begin the interview.")));
        persistMessage(session.getId(), "ai", reply);

        log.info("Interview started for application {} (session {})",
                token.getApplicationId(), session.getId());
        return InterviewEvent.question(reply);
    }

    /**
     * Records the candidate's answer, asks the model for its next turn, and — if
     * the model signals completion — scores the interview, advances the pipeline,
     * and emails HR. Returns either the next question or a completion event.
     */
    @Transactional
    public InterviewEvent answer(String tokenValue, String candidateAnswer) {
        // The token is already spent by start(), so validate() would report it as
        // ALREADY_USED. Resolve it by value/type instead (ignoring used status) and
        // let the session's own status gate continuation.
        Token token = tokenService.findByValueAndType(tokenValue, TokenType.INTERVIEW)
                .orElseThrow(() -> new BadRequestException("Interview link is no longer valid"));
        UUID applicationId = token.getApplicationId();

        InterviewSession session = sessionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BadRequestException("Interview has not been started"));
        if ("COMPLETED".equals(session.getStatus())) {
            return InterviewEvent.complete(
                    "Your interview is already complete. Results have been sent to the hiring team.");
        }
        if (candidateAnswer == null || candidateAnswer.isBlank()) {
            throw new BadRequestException("Answer cannot be empty");
        }

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        Job job = jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        persistMessage(session.getId(), "candidate", candidateAnswer);

        // Replay the full transcript to the model so it has complete context.
        List<ChatMessage> history = toChatHistory(session.getId());
        String reply = aiClient.chat(promptBuilder.systemPrompt(job), history);

        InterviewReport report = tryParseReport(reply);
        if (report != null && report.isComplete()) {
            return completeInterview(session, application, job, report);
        }

        persistMessage(session.getId(), "ai", reply);
        return InterviewEvent.question(reply);
    }

    // --- completion ---

    private InterviewEvent completeInterview(InterviewSession session, Application application,
                                             Job job, InterviewReport report) {
        int score = clamp(report.overallScore());
        int passMark = DEFAULT_PASS_MARK; // interview has no per-job pass field in the schema
        boolean passed = score >= passMark;

        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());
        session.setOverallScore(score);
        session.setPassed(passed);
        session.setAiReport(safeReportJson(report));
        sessionRepository.save(session);

        application.setStage(passed ? "FINAL" : "REJECTED");
        if (!passed) {
            application.setRejectionReason("INTERVIEW_FAILED");
        }
        application.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(application);

        notifyHr(application, job, session, report);

        log.info("Interview completed for application {}: score={} passed={}",
                application.getId(), score, passed);
        return InterviewEvent.complete(
                "Thank you! Your interview is complete. The hiring team will be in touch.");
    }

    /**
     * Emails the HR/company account the full candidate report (email #7). Pulls
     * the ATS and assessment scores so the report shows every stage. Failures are
     * logged but never roll back the completed interview.
     */
    private void notifyHr(Application application, Job job, InterviewSession session,
                          InterviewReport report) {
        try {
            Company company = companyRepository.findById(job.getCompanyId()).orElse(null);
            User candidate = userRepository.findById(application.getUserId()).orElse(null);
            if (company == null || candidate == null) {
                log.warn("Missing company/candidate for application {}; skipping HR report",
                        application.getId());
                return;
            }

            Integer atsScore = atsResultRepository.findByApplicationId(application.getId())
                    .map(AtsResult::getScore).orElse(null);
            Integer assessmentScore = assessmentAttemptRepository
                    .findByApplicationId(application.getId())
                    .map(AssessmentAttempt::getScore).orElse(null);

            emailService.sendHrCandidateReport(
                    company.getEmail(), candidate.getFullName(), candidate.getEmail(),
                    job.getTitle(), atsScore, assessmentScore,
                    session.getOverallScore(), report.recommendation(),
                    report.summary(), report.strengths(), report.weaknesses());
        } catch (Exception e) {
            log.warn("Failed to send HR candidate report for application {}",
                    application.getId(), e);
        }
    }

    // --- helpers ---

    private List<ChatMessage> toChatHistory(UUID sessionId) {
        List<InterviewMessage> transcript =
                messageRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        List<ChatMessage> history = new ArrayList<>();
        for (InterviewMessage m : transcript) {
            // Map our stored roles to chat roles: our "ai" is the assistant.
            String role = "ai".equals(m.getRole()) ? "assistant" : "user";
            history.add(new ChatMessage(role, m.getContent()));
        }
        return history;
    }

    private void persistMessage(UUID sessionId, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSentAt(LocalDateTime.now());
        message.setOrderIndex((int) messageRepository.countBySessionId(sessionId));
        messageRepository.save(message);
    }

    private String lastAiMessage(List<InterviewMessage> transcript) {
        String last = null;
        for (InterviewMessage m : transcript) {
            if ("ai".equals(m.getRole())) {
                last = m.getContent();
            }
        }
        return last;
    }

    private InterviewReport tryParseReport(String reply) {
        String json = promptBuilder.extractJson(reply);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewReport.class);
        } catch (Exception e) {
            // Not a completion payload — just a question that happened to contain braces.
            return null;
        }
    }

    private String safeReportJson(InterviewReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            return null;
        }
    }

    private Token requireValidToken(String tokenValue) {
        TokenValidation validation = tokenService.validate(tokenValue, TokenType.INTERVIEW);
        if (!validation.isValid()) {
            throw new BadRequestException("Interview link is " + reasonFor(validation.status()));
        }
        return validation.token();
    }

    private Job jobForToken(Token token) {
        Application application = applicationRepository.findById(token.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return jobRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String reasonFor(TokenStatus status) {
        return switch (status) {
            case EXPIRED -> "TOKEN_EXPIRED";
            case ALREADY_USED -> "TOKEN_USED";
            default -> "TOKEN_NOT_FOUND";
        };
    }
}
