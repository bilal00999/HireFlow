package com.example.demo.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EmailService}: verifies each template's recipient,
 * subject and body content, and that the assessment link is built from the
 * frontend URL plus the token value.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock EmailSender emailSender;

    EmailService emailService;

    @BeforeEach
    void setUp() {
        // Trailing slash on purpose — the service should normalize it away.
        emailService = new EmailService(emailSender, "http://localhost:5173/");
    }

    private EmailMessage capture() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void applicationReceived_hasSubjectAndName() {
        emailService.sendApplicationReceived("c@example.com", "Ada Lovelace", "Backend Developer");

        EmailMessage msg = capture();
        assertThat(msg.to()).isEqualTo("c@example.com");
        assertThat(msg.subject()).isEqualTo("We received your application for Backend Developer");
        assertThat(msg.body()).contains("Ada Lovelace").contains("Backend Developer");
    }

    @Test
    void atsRejection_isNeutralAndDoesNotLeakScore() {
        emailService.sendAtsRejection("c@example.com", "Ada Lovelace", "Backend Developer");

        EmailMessage msg = capture();
        assertThat(msg.subject()).isEqualTo("Application update for Backend Developer");
        assertThat(msg.body()).contains("Ada Lovelace");
        assertThat(msg.body()).doesNotContainIgnoringCase("score");
    }

    @Test
    void assessmentInvite_buildsLinkFromTokenAndNormalizesUrl() {
        LocalDateTime expiry = LocalDateTime.of(2026, 7, 15, 14, 30);

        emailService.sendAssessmentInvite("c@example.com", "Ada Lovelace", "Backend Developer",
                "Acme Corp", "tok-abc123", expiry, 45);

        EmailMessage msg = capture();
        assertThat(msg.subject())
                .isEqualTo("Next step: Complete your assessment for Backend Developer");
        // No double slash — trailing slash on the base URL was trimmed.
        assertThat(msg.body()).contains("http://localhost:5173/assessment?token=tok-abc123");
        assertThat(msg.body()).contains("Acme Corp");
        assertThat(msg.body()).contains("45-minute");
    }
}
