package com.example.demo.application;

import com.example.demo.application.dto.ApplicationDetailDto;
import com.example.demo.application.dto.DecisionRequest;
import com.example.demo.application.dto.DecisionRequest.Decision;
import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.email.EmailService;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * Unit tests for {@link ApplicationService#decide}: the HR final hire/reject
 * call. Covers the hire path (stage HIRED + offer email), the reject path
 * (stage REJECTED + rejection email), the "not at FINAL yet" guard, and the
 * non-owner guard. The owning company id is stashed in the security context so
 * {@code SecurityUtils.currentUserId()} resolves.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationDecisionTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock JobRepository jobRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock ResumeStorageService resumeStorage;
    @Mock com.example.demo.ats.AtsService atsService;
    @Mock EmailService emailService;

    ApplicationService service;

    UUID companyId;
    UUID jobId;
    UUID applicationId;
    Application application;
    Job job;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, jobRepository, userRepository,
                companyRepository, resumeStorage, atsService, emailService);

        companyId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        job = new Job();
        job.setId(jobId);
        job.setCompanyId(companyId);
        job.setTitle("Backend Developer");

        application = new Application();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setUserId(UUID.randomUUID());
        application.setStage("FINAL");

        authenticateAs(companyId);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID principalId) {
        var auth = new UsernamePasswordAuthenticationToken(
                principalId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_HR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void stubLookups() {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        User candidate = new User();
        candidate.setFullName("Casey Candidate");
        candidate.setEmail("casey@example.com");
        lenient().when(userRepository.findById(application.getUserId()))
                .thenReturn(Optional.of(candidate));
        lenient().when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(company("Acme")));
    }

    private Company company(String name) {
        Company c = new Company();
        c.setId(companyId);
        c.setName(name);
        return c;
    }

    @Test
    void hire_setsHiredStage_andSendsOffer() {
        stubLookups();

        ApplicationDetailDto dto = service.decide(applicationId,
                new DecisionRequest(Decision.HIRE, "We'll be in touch Monday."));

        assertThat(application.getStage()).isEqualTo("HIRED");
        assertThat(dto.stage()).isEqualTo("HIRED");
        verify(applicationRepository).save(application);
        verify(emailService).sendOffer(eq("casey@example.com"), eq("Casey Candidate"),
                eq("Backend Developer"), eq("Acme"), eq("We'll be in touch Monday."));
        verify(emailService, never()).sendFinalRejection(any(), any(), any(), any(), any());
    }

    @Test
    void reject_setsRejectedStage_andSendsRejection() {
        stubLookups();

        service.decide(applicationId, new DecisionRequest(Decision.REJECT, null));

        assertThat(application.getStage()).isEqualTo("REJECTED");
        assertThat(application.getRejectionReason()).isEqualTo("NOT_SELECTED");
        verify(emailService).sendFinalRejection(eq("casey@example.com"), eq("Casey Candidate"),
                eq("Backend Developer"), eq("Acme"), eq(null));
        verify(emailService, never()).sendOffer(any(), any(), any(), any(), any());
    }

    @Test
    void decide_beforeFinalStage_isRejected() {
        application.setStage("INTERVIEW");
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.decide(applicationId,
                new DecisionRequest(Decision.HIRE, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("final stage");
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void decide_onForeignCompanyJob_isRejected() {
        job.setCompanyId(UUID.randomUUID()); // owned by someone else
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.decide(applicationId,
                new DecisionRequest(Decision.REJECT, null)))
                .isInstanceOf(BadRequestException.class);
        verify(applicationRepository, never()).save(any());
    }
}
