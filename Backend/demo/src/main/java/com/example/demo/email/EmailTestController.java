package com.example.demo.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HR-only diagnostic endpoint to verify outbound email (Brevo SMTP) end-to-end
 * by sending one canned message to a chosen address.
 *
 * <p>Safety: the subject and body are fixed (no caller-supplied content), so it
 * can't be used as an open relay; it is guarded by {@code hasRole('HR')} on top
 * of the authenticated-only filter chain; and the whole controller can be
 * switched off with {@code app.email.test-endpoint.enabled=false} (it is not
 * even registered in the test context, where the property is absent). Delivery
 * uses whichever {@link EmailSender} is active, so in log mode it only logs.
 */
@RestController
@RequestMapping("/api/v1/hr/email")
@ConditionalOnProperty(name = "app.email.test-endpoint.enabled", havingValue = "true")
@Validated
public class EmailTestController {

    private static final Logger log = LoggerFactory.getLogger(EmailTestController.class);

    private final EmailSender emailSender;

    public EmailTestController(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    /** Sends one canned test email to {@code to}. Requires an HR token. */
    @PostMapping("/test")
    @PreAuthorize("hasRole('HR')")
    public Map<String, String> sendTest(@RequestParam @NotBlank @Email String to) {
        emailSender.send(new EmailMessage(
                to,
                "HireFlow SMTP test",
                """
                This is a test email from HireFlow.

                If you're reading this, outbound email via Brevo SMTP is working.
                """));
        log.info("Dispatched test email to {}", to);
        return Map.of("status", "sent", "to", to);
    }
}
