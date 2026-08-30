package com.example.demo.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link EmailSender} that "delivers" mail by logging it to the console
 * instead of contacting a provider. Active when {@code app.email.provider=log}
 * or the property is unset; when it is {@code brevo}, {@link BrevoEmailSender}
 * takes over. This lets the whole pipeline be developed and tested end-to-end
 * with zero external accounts, and keeps tests off real SMTP.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("""

                ┌──────────────────────────────────────────────────────────
                │ [EMAIL — log only, not actually sent]
                │ To:      {}
                │ Subject: {}
                ├──────────────────────────────────────────────────────────
                {}
                └──────────────────────────────────────────────────────────
                """, message.to(), message.subject(), indentBody(message.body()));
    }

    private String indentBody(String body) {
        return body.lines()
                .map(line -> "│ " + line)
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("│ (empty)");
    }
}
