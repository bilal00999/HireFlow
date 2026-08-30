package com.example.demo.email;

/**
 * Thrown when an {@link EmailSender} fails to hand a message to its transport
 * (e.g. an SMTP authentication or connection failure). Carries a sanitized
 * message that never includes credentials or provider internals. Pipeline
 * callers already treat mail as best-effort — the synchronous apply path
 * swallows it and background sends run under {@code @Async} — so this surfaces
 * the failure in logs and to diagnostic callers without derailing core work.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
