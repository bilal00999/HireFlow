package com.example.demo.assessment;

import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.assessment.dto.*;
import com.example.demo.common.BadRequestException;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import com.example.demo.token.Token;
import com.example.demo.token.TokenService;
import com.example.demo.token.TokenStatus;
import com.example.demo.token.TokenType;
import com.example.demo.token.TokenValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AssessmentService}: token verification outcomes, the
 * candidate question view (no answer keys), attempt creation, and submit
 * behavior (token consumed, grading kicked off, double-submit rejected).
 */
@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock TokenService tokenService;
    @Mock ApplicationRepository applicationRepository;
    @Mock JobRepository jobRepository;
    @Mock AssessmentQuestionRepository questionRepository;
    @Mock AssessmentAttemptRepository attemptRepository;
    @Mock AssessmentAnswerRepository answerRepository;
    @Mock AssessmentGradingService gradingService;

    AssessmentService service;

    UUID applicationId;
    UUID jobId;
    Application application;
    Job job;
    Token token;

    @BeforeEach
    void setUp() {
        service = new AssessmentService(tokenService, applicationRepository, jobRepository,
                questionRepository, attemptRepository, answerRepository, gradingService);

        applicationId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        application = new Application();
        application.setId(applicationId);
        application.setJobId(jobId);

        job = new Job();
        job.setId(jobId);
        job.setTitle("Backend Developer");
        job.setAssessmentTimeLimit(30);

        token = new Token();
        token.setId(UUID.randomUUID());
        token.setApplicationId(applicationId);
        token.setTokenValue("tok-abc");
        token.setExpiresAt(LocalDateTime.now().plusHours(24));

        lenient().when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(application));
        lenient().when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
    }

    private AssessmentQuestion mcq() {
        AssessmentQuestion q = new AssessmentQuestion();
        q.setId(UUID.randomUUID());
        q.setJobId(jobId);
        q.setQuestionType("MCQ");
        q.setQuestionText("Which is O(1)?");
        q.setOptions(List.of("a", "b", "c", "d"));
        q.setCorrectOption(2);
        q.setMaxScore(10);
        return q;
    }

    @Test
    void verify_validToken_returnsJobDetails() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.valid(token));
        when(questionRepository.countByJobId(jobId)).thenReturn(5L);

        VerifyTokenResponse res = service.verify("tok-abc");

        assertThat(res.valid()).isTrue();
        assertThat(res.jobTitle()).isEqualTo("Backend Developer");
        assertThat(res.questionCount()).isEqualTo(5);
        assertThat(res.timeLimit()).isEqualTo(30);
    }

    @Test
    void verify_expiredToken_returnsReasonNotException() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.invalid(TokenStatus.EXPIRED));

        VerifyTokenResponse res = service.verify("tok-abc");

        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).isEqualTo("TOKEN_EXPIRED");
        assertThat(res.jobTitle()).isNull();
    }

    @Test
    void verify_usedToken_mapsToTokenUsed() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.invalid(TokenStatus.ALREADY_USED));

        assertThat(service.verify("tok-abc").reason()).isEqualTo("TOKEN_USED");
    }

    @Test
    void getQuestions_hidesCorrectOption_andOpensAttempt() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.valid(token));
        when(attemptRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId))
                .thenReturn(List.of(mcq()));

        AssessmentQuestionsResponse res = service.getQuestions("tok-abc");

        assertThat(res.questions()).hasSize(1);
        CandidateQuestionDto dto = res.questions().get(0);
        assertThat(dto.options()).containsExactly("a", "b", "c", "d");
        // The DTO record has no correctOption field at all — answer key never ships.
        verify(attemptRepository).save(any());
        // Fetching questions must NOT consume the token (reload-safe).
        verify(tokenService, never()).markUsed(any());
    }

    @Test
    void getQuestions_invalidToken_throws() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.invalid(TokenStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getQuestions("tok-abc"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void submit_persistsAnswers_consumesToken_triggersGrading() {
        AssessmentQuestion q = mcq();
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.valid(token));
        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setApplicationId(applicationId);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(5));
        when(attemptRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.findByAttemptId(attempt.getId())).thenReturn(List.of());
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of(q));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAssessmentRequest req = new SubmitAssessmentRequest(List.of(
                new SubmitAssessmentRequest.AnswerInput(q.getId().toString(), 2, null)));

        SubmitAssessmentResponse res = service.submit("tok-abc", req);

        assertThat(res.submitted()).isTrue();
        verify(answerRepository).save(any());
        verify(tokenService).markUsed(token);
        verify(gradingService).gradeAsync(attempt.getId());
        assertThat(attempt.getSubmittedAt()).isNotNull();
    }

    @Test
    void submit_answerForForeignQuestion_rejected() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.valid(token));
        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setApplicationId(applicationId);
        attempt.setStartedAt(LocalDateTime.now());
        when(attemptRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.of(attempt));
        when(answerRepository.findByAttemptId(attempt.getId())).thenReturn(List.of());
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of(mcq()));

        SubmitAssessmentRequest req = new SubmitAssessmentRequest(List.of(
                new SubmitAssessmentRequest.AnswerInput(UUID.randomUUID().toString(), 1, null)));

        assertThatThrownBy(() -> service.submit("tok-abc", req))
                .isInstanceOf(BadRequestException.class);
        verify(tokenService, never()).markUsed(any());
        verify(gradingService, never()).gradeAsync(any());
    }

    @Test
    void submit_secondTime_rejectedAsAlreadySubmitted() {
        when(tokenService.validate("tok-abc", TokenType.ASSESSMENT))
                .thenReturn(TokenValidation.valid(token));
        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setApplicationId(applicationId);
        attempt.setStartedAt(LocalDateTime.now());
        when(attemptRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.of(attempt));
        AssessmentAnswer existing = new AssessmentAnswer();
        when(answerRepository.findByAttemptId(attempt.getId())).thenReturn(List.of(existing));

        SubmitAssessmentRequest req = new SubmitAssessmentRequest(List.of());

        assertThatThrownBy(() -> service.submit("tok-abc", req))
                .isInstanceOf(BadRequestException.class);
        verify(gradingService, never()).gradeAsync(any());
    }
}
