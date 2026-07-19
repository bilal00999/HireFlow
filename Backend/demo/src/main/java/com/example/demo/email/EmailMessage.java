package com.example.demo.email;

/**
 * A rendered, ready-to-send email. Plain text keeps the transport simple and
 * provider-agnostic; HTML templating can be layered on later without changing
 * the {@link EmailSender} contract.
 */
public record EmailMessage(String to, String subject, String body) {}
