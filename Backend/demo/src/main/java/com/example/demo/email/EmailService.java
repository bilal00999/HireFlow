package com.example.demo.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds and sends the candidate-facing emails for the hiring pipeline. Message
 * bodies follow the templates in 08-EMAIL-SYSTEM.md. Rendering lives here;
 * delivery is delegated to whichever {@link EmailSender} is active (log-only by
 * default), so this class is transport-agnostic.
 */
@Service
public class EmailService {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a");

    private final EmailSender emailSender;
    private final String frontendUrl;

    public EmailService(EmailSender emailSender,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.emailSender = emailSender;
        // Trim a trailing slash so link building is predictable.
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    /** Email #1 — sent immediately after a candidate applies. */
    public void sendApplicationReceived(String candidateEmail, String candidateName, String jobTitle) {
        String subject = "We received your application for " + jobTitle;
        String body = """
                Hi %s,

                Thanks for applying for the %s position. We've received your application
                and our team is reviewing it now.

                We'll be in touch with the next steps soon.

                The Hiring Team
                """.formatted(candidateName, jobTitle);
        emailSender.send(new EmailMessage(candidateEmail, subject, body));
    }

    /** Email #2 — sent when a resume falls below the ATS threshold. */
    public void sendAtsRejection(String candidateEmail, String candidateName, String jobTitle) {
        String subject = "Application update for " + jobTitle;
        String body = """
                Hi %s,

                Thank you for your interest in the %s position and for taking the time
                to apply.

                After careful review, we've decided not to move forward with your
                application at this time. This decision isn't a reflection of your
                abilities, and we encourage you to apply for future roles that match
                your experience.

                We wish you the best in your job search.

                The Hiring Team
                """.formatted(candidateName, jobTitle);
        emailSender.send(new EmailMessage(candidateEmail, subject, body));
    }

    /**
     * Email #3 — sent when a resume passes ATS. Carries the single-use
     * assessment link built from the token, plus the expiry and format details.
     */
    public void sendAssessmentInvite(String candidateEmail, String candidateName, String jobTitle,
                                     String companyName, String tokenValue, LocalDateTime expiresAt,
                                     int timeLimitMinutes) {
        String link = frontendUrl + "/assessment?token=" + tokenValue;
        String subject = "Next step: Complete your assessment for " + jobTitle;
        String body = """
                Hi %s,

                Great news! Your resume for the %s position at %s has been reviewed and
                we'd like to invite you to the next step.

                Please complete the online assessment using the link below:

                TAKE ASSESSMENT: %s

                Important notes:
                - This link expires on %s
                - The assessment has a %d-minute time limit
                - You can only use this link once
                - Make sure you're in a quiet place before starting

                Good luck!
                The %s Hiring Team
                """.formatted(candidateName, jobTitle, companyName, link,
                expiresAt.format(EXPIRY_FORMAT), timeLimitMinutes, companyName);
        emailSender.send(new EmailMessage(candidateEmail, subject, body));
    }

    /** Email #5 — sent when an assessment score falls below the pass mark. */
    public void sendAssessmentRejection(String candidateEmail, String candidateName, String jobTitle) {
        String subject = "Application update for " + jobTitle;
        String body = """
                Hi %s,

                Thank you for completing the assessment for the %s position.

                After reviewing your results, we've decided not to move forward with
                your application at this time. We genuinely appreciate the effort you
                put in, and we encourage you to apply for future roles that match your
                experience.

                We wish you the best in your job search.

                The Hiring Team
                """.formatted(candidateName, jobTitle);
        emailSender.send(new EmailMessage(candidateEmail, subject, body));
    }

    /**
     * Email #6 — sent when an assessment passes. Carries the single-use
     * interview link built from the token, plus expiry and format details.
     */
    public void sendInterviewInvite(String candidateEmail, String candidateName, String jobTitle,
                                    String companyName, String tokenValue, LocalDateTime expiresAt,
                                    int durationMinutes) {
        String link = frontendUrl + "/interview?token=" + tokenValue;
        String subject = "You've been selected for an AI Interview!";
        String body = """
                Hi %s,

                Congratulations! You've passed the assessment for the %s position at %s
                and we'd like to invite you to an AI-guided interview.

                Start your interview using the link below:

                START INTERVIEW: %s

                Important notes:
                - This link expires on %s
                - The interview takes about %d minutes
                - You can only use this link once
                - Find a quiet place with a stable connection before starting

                Good luck!
                The %s Hiring Team
                """.formatted(candidateName, jobTitle, companyName, link,
                expiresAt.format(EXPIRY_FORMAT), durationMinutes, companyName);
        emailSender.send(new EmailMessage(candidateEmail, subject, body));
    }
}
