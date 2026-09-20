package com.example.demo.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Production {@link EmailSender} backed by Brevo's SMTP relay. Active when
 * {@code app.email.provider=brevo} (the configured default); the app falls back
 * to {@link LoggingEmailSender} when the provider is {@code log} or unset, so
 * tests and credential-less environments never open an SMTP connection.
 *
 * <p>Delivery goes through Spring's {@link JavaMailSender}, which is configured
 * from the standard {@code spring.mail.*} properties (host/port/auth/STARTTLS).
 * The SMTP login and key are supplied via environment variables — never source
 * code — and never appear in logs or exceptions. The sender identity uses a
 * verified Brevo address ({@code app.email.from}) and display name
 * ({@code app.email.from-name}), rendered as e.g. {@code HireFlow <addr>}.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(JavaMailSender mailSender,
                            @Value("${app.email.from:bilalahmed20051@gmail.com}") String fromEmail,
                            @Value("${app.email.from-name:HireFlow}") String fromName,
                            @Value("${spring.mail.password:}") String smtpKey) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        warnIfSmtpKeyLooksWrong(smtpKey);
    }

    /**
     * Startup sanity check on the configured SMTP secret. Brevo's SMTP relay
     * authenticates with the <b>SMTP key</b> ({@code xsmtpsib-…}); the REST
     * <b>API v3 key</b> ({@code xkeysib-…}) is rejected with "535 Authentication
     * failed", which — because sends are best-effort — silently drops every mail.
     * We log a clear WARN (never the key itself, only its non-secret prefix type)
     * so the misconfiguration is obvious in the logs instead of invisible.
     */
    private void warnIfSmtpKeyLooksWrong(String smtpKey) {
        if (smtpKey == null || smtpKey.isBlank()) {
            log.warn("Email provider is 'brevo' but no SMTP key is configured "
                    + "(BREVO_SMTP_KEY is empty); every email will fail SMTP authentication. "
                    + "Set the Brevo SMTP key in .env.");
        } else if (smtpKey.startsWith("xkeysib-")) {
            log.warn("BREVO_SMTP_KEY looks like a Brevo API v3 key (xkeysib-…), which the SMTP "
                    + "relay rejects with '535 Authentication failed'. Use the SMTP key "
                    + "(xsmtpsib-…) from Brevo -> SMTP & API -> SMTP tab instead.");
        }
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // multipart=false, UTF-8: a simple plain-text message, matching the
            // EmailMessage contract (HTML can be layered on later if needed).
            MimeMessageHelper helper =
                    new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            mailSender.send(mime);
            log.info("Sent email to {} via Brevo SMTP (subject: '{}')",
                    message.to(), message.subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            // Log enough to debug, but never the SMTP credentials: they live only
            // in spring.mail.* / the environment and are not part of the exception.
            // Re-throw a sanitized runtime exception whose message omits provider
            // internals. Callers treat mail as best-effort (see ApplicationService)
            // and async sends are isolated, so this never derails the pipeline.
            log.error("Failed to send email to {} (subject: '{}'): {}",
                    message.to(), message.subject(), e.getMessage());
            throw new EmailDeliveryException(
                    "Email delivery failed for recipient " + message.to(), e);
        }
    }
}
