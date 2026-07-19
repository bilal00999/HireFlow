package com.example.demo.email;

/**
 * Transport abstraction for outbound email. The rest of the app depends only on
 * this interface, so switching providers (log-only in dev, SMTP/Mailgun/Resend
 * in production) is a matter of supplying a different bean — no caller changes.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
