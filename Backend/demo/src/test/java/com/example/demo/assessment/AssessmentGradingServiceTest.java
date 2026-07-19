package com.example.demo.assessment;

import com.example.demo.ai.AiUnavailableException;
import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.email.EmailService;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.example.demo.token.Token;
import com.example.demo.token.TokenType;
import com.example.demo.ai.AiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AssessmentGradingService} with collaborators mocked.
 * Covers MCQ auto-grading, AI text grading (including its failure fallback),
 * the score normalization, and the pass/fail pipeline transitions.
 */
@ExtendWith(MockitoExtension.class)
class AssessmentGradingServiceTest {

    @Mock AssessmentAttemptRepository attemptRepository;
    @Mock AssessmentAnswerRepository answerRepository;
    @Mock AssessmentQuestionRepository questionRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock JobRepository jobRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock AiClient aiClient;
    @Mock com.example.demo.token.TokenService tokenService;
    @Mock EmailService emailService;

    AssessmentGradingPromptBuilder promptBuilder = new AssessmentGradingPromptBuilder();
    ObjectMapper objectMapper = new ObjectMapper();

    AssessmentGradingService gradingService;

    UUID attemptId;
    UUID applicationId;
    UUID jobId;
    UUID companyId;
    AssessmentAttempt attempt;
    Application application;
    Job job;

    @BeforeEach
    void setUp() {
        gradingService = new AssessmentGradingService(attemptRepository, answerRepository,
                questionRepository, applicationRepository, jobRepository, userRepository,
                companyRepository, promptBuilder, aiClient, objectMapper, tokenService,
                emailService);

        attemptId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        attempt = new AssessmentAttempt();
        attempt.setId(attemptId);
        attempt.setApplicationId(applicationId);

        application = new Application();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setUserId(UUID.randomUUID());
        application.setStage("ASSESSMENT");

        job = new Job();
        job.setId(jobId);
        job.setCompanyId(companyId);
        job.setTitle("Backend Developer");
        job.setAssessmentPassScore(70);
        job.setInterviewDuration(25);

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        // The already-scored (idempotent) test returns before touching these,
        // so they're shared setup for the grading tests and marked lenient.
        lenient().when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        lenient().when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        lenient().when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User candidate = new User();
        candidate.setEmail("cand@example.com");
        candidate.setFullName("Cand Idate");
        lenient().when(userRepository.findById(application.getUserId()))
                .thenReturn(Optional.of(candidate));
        lenient().when(companyRepository.findById(companyId)).thenReturn(Optional.empty());
    }

    private AssessmentQuestion mcq(int correctOption, int maxScore) {
        AssessmentQuestion q = new AssessmentQuestion();
        q.setId(UUID.randomUUID());
        q.setJobId(jobId);
        q.setQuestionType("MCQ");
        q.setQuestionText("Which is O(1)?");
        q.setOptions(List.of("a", "b", "c", "d"));
        q.setCorrectOption(correctOption);
        q.setMaxScore(maxScore);
        return q;
    }

    private AssessmentQuestion text(int maxScore) {
        AssessmentQuestion q = new AssessmentQuestion();
        q.setId(UUID.randomUUID());
        q.setJobId(jobId);
        q.setQuestionType("TEXT");
        q.setQuestionText("Explain polymorphism.");
        q.setMaxScore(maxScore);
        return q;
    }

    private AssessmentAnswer mcqAnswer(UUID questionId, int selected) {
        AssessmentAnswer a = new AssessmentAnswer();
        a.setId(UUID.randomUUID());
        a.setAttemptId(attemptId);
        a.setQuestionId(questionId);
        a.setSelectedOption(selected);
        return a;
    }

    private AssessmentAnswer textAnswer(UUID questionId, String body) {
        AssessmentAnswer a = new AssessmentAnswer();
        a.setId(UUID.randomUUID());
        a.setAttemptId(attemptId);
        a.setQuestionId(questionId);
        a.setAnswerText(body);
        return a;
    }

    @Test
    void grade_allMcqCorrect_passes_advancesToInterview_issuesTokenAndInvite() {
        AssessmentQuestion q1 = mcq(2, 10);
        AssessmentQuestion q2 = mcq(0, 10);
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId))
                .thenReturn(List.of(q1, q2));
        when(answerRepository.findByAttemptId(attemptId))
                .thenReturn(List.of(mcqAnswer(q1.getId(), 2), mcqAnswer(q2.getId(), 0)));

        Token token = new Token();
        token.setTokenValue("intv-tok");
        token.setExpiresAt(java.time.LocalDateTime.now().plusHours(24));
        when(tokenService.create(eq(applicationId), eq(TokenType.INTERVIEW), any()))
                .thenReturn(token);

        gradingService.grade(attemptId);

        assertThat(attempt.getScore()).isEqualTo(100);
        assertThat(attempt.getPassed()).isTrue();
        assertThat(application.getStage()).isEqualTo("INTERVIEW");
        assertThat(application.getRejectionReason()).isNull();

        verify(tokenService).create(eq(applicationId), eq(TokenType.INTERVIEW), any());
        verify(emailService).sendInterviewInvite(eq("cand@example.com"), eq("Cand Idate"),
                eq("Backend Developer"), anyString(), eq("intv-tok"), any(), anyInt());
        verify(emailService, never()).sendAssessmentRejection(anyString(), anyString(), anyString());
    }

    @Test
    void grade_belowPassMark_rejects_sendsRejectionNoToken() {
        AssessmentQuestion q1 = mcq(2, 10);
        AssessmentQuestion q2 = mcq(0, 10);
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId))
                .thenReturn(List.of(q1, q2));
        // Only the first answer is correct -> 50, below the pass mark of 70.
        when(answerRepository.findByAttemptId(attemptId))
                .thenReturn(List.of(mcqAnswer(q1.getId(), 2), mcqAnswer(q2.getId(), 3)));

        gradingService.grade(attemptId);

        assertThat(attempt.getScore()).isEqualTo(50);
        assertThat(attempt.getPassed()).isFalse();
        assertThat(application.getStage()).isEqualTo("REJECTED");
        assertThat(application.getRejectionReason()).isEqualTo("ASSESSMENT_FAILED");

        verify(emailService).sendAssessmentRejection("cand@example.com", "Cand Idate",
                "Backend Developer");
        verify(tokenService, never()).create(any(), any(), any());
        verify(emailService, never()).sendInterviewInvite(anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyInt());
    }

    @Test
    void grade_textAnswer_scoredByAi_clampedToMax() {
        AssessmentQuestion q = text(10);
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of(q));
        AssessmentAnswer answer = textAnswer(q.getId(), "Polymorphism is...");
        when(answerRepository.findByAttemptId(attemptId)).thenReturn(List.of(answer));
        // Model over-reports 15 for a max of 10; grader must clamp to 10 -> score 100.
        when(aiClient.complete(anyString(), anyString()))
                .thenReturn("{\"score\": 15, \"feedback\": \"Great answer.\"}");

        Token token = new Token();
        token.setTokenValue("intv-tok");
        token.setExpiresAt(java.time.LocalDateTime.now().plusHours(24));
        when(tokenService.create(any(), eq(TokenType.INTERVIEW), any())).thenReturn(token);

        gradingService.grade(attemptId);

        assertThat(answer.getAiScore()).isEqualTo(10);
        assertThat(answer.getAiFeedback()).isEqualTo("Great answer.");
        assertThat(attempt.getScore()).isEqualTo(100);
        assertThat(attempt.getPassed()).isTrue();
    }

    @Test
    void grade_textAnswer_aiFailure_awardsZeroButStillCompletes() {
        AssessmentQuestion q = text(10);
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of(q));
        AssessmentAnswer answer = textAnswer(q.getId(), "some answer");
        when(answerRepository.findByAttemptId(attemptId)).thenReturn(List.of(answer));
        when(aiClient.complete(anyString(), anyString()))
                .thenThrow(new AiUnavailableException("model down", new RuntimeException("boom")));

        gradingService.grade(attemptId);

        assertThat(answer.getAiScore()).isEqualTo(0);
        assertThat(attempt.getScore()).isEqualTo(0);
        assertThat(attempt.getPassed()).isFalse();
        assertThat(application.getStage()).isEqualTo("REJECTED");
    }

    @Test
    void grade_whenAlreadyScored_isIdempotent() {
        attempt.setScore(88);
        attempt.setPassed(true);

        gradingService.grade(attemptId);

        verify(questionRepository, never()).findByJobIdOrderByOrderIndexAsc(any());
        verify(aiClient, never()).complete(anyString(), anyString());
        verify(emailService, never()).sendInterviewInvite(anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyInt());
    }

    @Test
    void grade_noQuestions_treatedAsPass() {
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of());
        when(answerRepository.findByAttemptId(attemptId)).thenReturn(List.of());
        Token token = new Token();
        token.setTokenValue("intv-tok");
        token.setExpiresAt(java.time.LocalDateTime.now().plusHours(24));
        when(tokenService.create(any(), eq(TokenType.INTERVIEW), any())).thenReturn(token);

        gradingService.grade(attemptId);

        assertThat(attempt.getScore()).isEqualTo(100);
        assertThat(attempt.getPassed()).isTrue();
        assertThat(application.getStage()).isEqualTo("INTERVIEW");
    }
}
