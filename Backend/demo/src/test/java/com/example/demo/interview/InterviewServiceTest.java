package com.example.demo.interview;

import com.example.demo.ai.AiClient;
import com.example.demo.application.Application;
import com.example.demo.application.ApplicationRepository;
import com.example.demo.assessment.AssessmentAttempt;
import com.example.demo.assessment.AssessmentAttemptRepository;
import com.example.demo.ats.AtsResult;
import com.example.demo.ats.AtsResultRepository;
import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InterviewService}: token verification, starting a
 * session (first question + token consumed), continuing with answers, and the
 * completion path (scoring, stage transition, HR report email). All
 * collaborators are mocked; the prompt builder and object mapper are real.
 */
@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock TokenService tokenService;
    @Mock ApplicationRepository applicationRepository;
    @Mock JobRepository jobRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock AtsResultRepository atsResultRepository;
    @Mock AssessmentAttemptRepository assessmentAttemptRepository;
    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock AiClient aiClient;
    @Mock EmailService emailService;

    InterviewPromptBuilder promptBuilder = new InterviewPromptBuilder();
    ObjectMapper objectMapper = new ObjectMapper();

    InterviewService service;

    UUID applicationId;
    UUID jobId;
    Application application;
    Job job;
    Token token;

    @BeforeEach
    void setUp() {
        service = new InterviewService(tokenService, applicationRepository, jobRepository,
                userRepository, companyRepository, atsResultRepository, assessmentAttemptRepository,
                sessionRepository, messageRepository, promptBuilder, aiClient, objectMapper,
                emailService);

        applicationId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        application = new Application();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setUserId(UUID.randomUUID());
        application.setStage("INTERVIEW");

        job = new Job();
        job.setId(jobId);
        job.setCompanyId(UUID.randomUUID());
        job.setTitle("Backend Developer");
        job.setInterviewTopics(List.of("Java", "REST"));
        job.setInterviewNumQuestions(3);
        job.setInterviewDuration(20);

        token = new Token();
        token.setId(UUID.randomUUID());
        token.setApplicationId(applicationId);
        token.setTokenType(TokenType.INTERVIEW.name());
    }

    private void stubTokenAndJob() {
        lenient().when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(application));
        lenient().when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
    }

    // --- verify ---

    @Test
    void verify_validToken_returnsJobDetails() {
        when(tokenService.validate("tok", TokenType.INTERVIEW))
                .thenReturn(TokenValidation.valid(token));
        stubTokenAndJob();

        VerifyInterviewResponse res = service.verify("tok");

        assertThat(res.valid()).isTrue();
        assertThat(res.jobTitle()).isEqualTo("Backend Developer");
        assertThat(res.durationMinutes()).isEqualTo(20);
    }

    @Test
    void verify_expiredToken_returnsInvalidWithReason() {
        when(tokenService.validate("tok", TokenType.INTERVIEW))
                .thenReturn(TokenValidation.invalid(TokenStatus.EXPIRED));

        VerifyInterviewResponse res = service.verify("tok");

        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).isEqualTo("TOKEN_EXPIRED");
    }

    // --- start ---

    @Test
    void start_freshSession_consumesToken_persistsFirstQuestion() {
        when(tokenService.validate("tok", TokenType.INTERVIEW))
                .thenReturn(TokenValidation.valid(token));
        stubTokenAndJob();
        when(sessionRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(messageRepository.countBySessionId(any())).thenReturn(0L);
        when(aiClient.chat(anyString(), anyList()))
                .thenReturn("Hello! Tell me about your experience with Java.");

        InterviewEvent event = service.start("tok");

        assertThat(event.type()).isEqualTo(InterviewEvent.Type.QUESTION);
        assertThat(event.content()).contains("Java");
        verify(tokenService).markUsed(token);
        verify(messageRepository).save(any(InterviewMessage.class));
    }

    @Test
    void start_completedSession_returnsCompleteWithoutCallingAi() {
        when(tokenService.validate("tok", TokenType.INTERVIEW))
                .thenReturn(TokenValidation.valid(token));
        stubTokenAndJob();
        InterviewSession done = new InterviewSession();
        done.setId(UUID.randomUUID());
        done.setApplicationId(applicationId);
        done.setStatus("COMPLETED");
        when(sessionRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(done));

        InterviewEvent event = service.start("tok");

        assertThat(event.type()).isEqualTo(InterviewEvent.Type.COMPLETE);
        verify(aiClient, never()).chat(anyString(), anyList());
        verify(tokenService, never()).markUsed(any());
    }

    @Test
    void start_invalidToken_throws() {
        when(tokenService.validate("bad", TokenType.INTERVIEW))
                .thenReturn(TokenValidation.invalid(TokenStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.start("bad"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- answer ---

    @Test
    void answer_midInterview_returnsNextQuestion() {
        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setApplicationId(applicationId);
        session.setStatus("IN_PROGRESS");

        when(tokenService.findByValueAndType("tok", TokenType.INTERVIEW))
                .thenReturn(Optional.of(token));
        when(sessionRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(session));
        stubTokenAndJob();
        when(messageRepository.countBySessionId(session.getId())).thenReturn(1L, 2L);
        when(messageRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(aiMsg("Q1"), candidateMsg("my answer")));
        when(aiClient.chat(anyString(), anyList())).thenReturn("Good. Now describe REST.");

        InterviewEvent event = service.answer("tok", "my answer");

        assertThat(event.type()).isEqualTo(InterviewEvent.Type.QUESTION);
        assertThat(event.content()).contains("REST");
    }

    @Test
    void answer_completionJson_scoresAndEmailsHr_andPasses() {
        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setApplicationId(applicationId);
        session.setStatus("IN_PROGRESS");

        when(tokenService.findByValueAndType("tok", TokenType.INTERVIEW))
                .thenReturn(Optional.of(token));
        when(sessionRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(session));
        stubTokenAndJob();
        when(messageRepository.countBySessionId(session.getId())).thenReturn(4L);
        when(messageRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(aiMsg("Q"), candidateMsg("a")));
        when(aiClient.chat(anyString(), anyList())).thenReturn("""
                {"type":"INTERVIEW_COMPLETE","overall_score":82,
                 "strengths":["clear communication"],"weaknesses":["shallow on caching"],
                 "recommendation":"HIRE","summary":"Strong candidate."}
                """);

        User candidate = new User();
        candidate.setFullName("Cand Idate");
        candidate.setEmail("cand@example.com");
        when(userRepository.findById(application.getUserId())).thenReturn(Optional.of(candidate));
        Company company = new Company();
        company.setName("Acme");
        company.setEmail("hr@acme.com");
        when(companyRepository.findById(job.getCompanyId())).thenReturn(Optional.of(company));
        when(atsResultRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        when(assessmentAttemptRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.empty());

        InterviewEvent event = service.answer("tok", "final answer");

        assertThat(event.type()).isEqualTo(InterviewEvent.Type.COMPLETE);
        assertThat(session.getStatus()).isEqualTo("COMPLETED");
        assertThat(session.getOverallScore()).isEqualTo(82);
        assertThat(session.getPassed()).isTrue();
        assertThat(application.getStage()).isEqualTo("FINAL");
        verify(emailService).sendHrCandidateReport(eq("hr@acme.com"), eq("Cand Idate"),
                eq("cand@example.com"), eq("Backend Developer"), any(), any(),
                eq(82), eq("HIRE"), anyString(), anyList(), anyList());
    }

    @Test
    void answer_lowScore_rejectsWithInterviewFailed() {
        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setApplicationId(applicationId);
        session.setStatus("IN_PROGRESS");

        when(tokenService.findByValueAndType("tok", TokenType.INTERVIEW))
                .thenReturn(Optional.of(token));
        when(sessionRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(session));
        stubTokenAndJob();
        when(messageRepository.countBySessionId(session.getId())).thenReturn(4L);
        when(messageRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()))
                .thenReturn(List.of(aiMsg("Q"), candidateMsg("a")));
        when(aiClient.chat(anyString(), anyList())).thenReturn("""
                {"type":"INTERVIEW_COMPLETE","overall_score":35,
                 "strengths":[],"weaknesses":["weak fundamentals"],
                 "recommendation":"REJECT","summary":"Not ready."}
                """);
        when(userRepository.findById(application.getUserId())).thenReturn(Optional.empty());
        lenient().when(companyRepository.findById(job.getCompanyId())).thenReturn(Optional.empty());

        InterviewEvent event = service.answer("tok", "final answer");

        assertThat(event.type()).isEqualTo(InterviewEvent.Type.COMPLETE);
        assertThat(application.getStage()).isEqualTo("REJECTED");
        assertThat(application.getRejectionReason()).isEqualTo("INTERVIEW_FAILED");
    }

    @Test
    void answer_emptyContent_throws() {
        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID());
        session.setApplicationId(applicationId);
        session.setStatus("IN_PROGRESS");

        when(tokenService.findByValueAndType("tok", TokenType.INTERVIEW))
                .thenReturn(Optional.of(token));
        when(sessionRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.answer("tok", "  "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void answer_unknownToken_throws() {
        when(tokenService.findByValueAndType("bad", TokenType.INTERVIEW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.answer("bad", "hello"))
                .isInstanceOf(BadRequestException.class);
    }

    private InterviewMessage aiMsg(String content) {
        InterviewMessage m = new InterviewMessage();
        m.setRole("ai");
        m.setContent(content);
        return m;
    }

    private InterviewMessage candidateMsg(String content) {
        InterviewMessage m = new InterviewMessage();
        m.setRole("candidate");
        m.setContent(content);
        return m;
    }
}
