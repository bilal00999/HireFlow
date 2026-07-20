package com.example.demo.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    /**
     * Email #7 — sent to HR after a candidate completes the AI interview and
     * clears the full pipeline. Summarizes every stage score plus the interview
     * report so HR can make a final decision.
     */
    public void sendHrCandidateReport(String hrEmail, String candidateName, String candidateEmail,
                                      String jobTitle, Integer atsScore, Integer assessmentScore,
                                      int interviewScore, String recommendation,
                                      String interviewSummary, List<String> strengths,
                                      List<String> weaknesses) {
        int ats = atsScore != null ? atsScore : 0;
        int assessment = assessmentScore != null ? assessmentScore : 0;
        int overall = Math.round((ats + assessment + interviewScore) / 3.0f);

        String subject = "Candidate Report: " + candidateName + " for " + jobTitle;
        String body = """
                A candidate has completed the full automated pipeline for %s.

                CANDIDATE: %s
                EMAIL: %s
                JOB: %s

                SCORES:
                  ATS Resume Score:   %d/100
                  Assessment Score:   %d/100
                  AI Interview Score: %d/100
                  Overall Average:    %d/100

                AI INTERVIEW SUMMARY:
                %s

                STRENGTHS:
                %s

                AREAS FOR IMPROVEMENT:
                %s

                AI RECOMMENDATION: %s

                You can reach out to the candidate directly at %s or log in to
                HireFlow to send an official decision.
                """.formatted(jobTitle, candidateName, candidateEmail, jobTitle,
                ats, assessment, interviewScore, overall,
                blankToDash(interviewSummary), bulletList(strengths), bulletList(weaknesses),
                recommendation != null ? recommendation : "N/A", candidateEmail);
        emailSender.send(new EmailMessage(hrEmail, subject, body));
    }

    private String bulletList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "  - (none noted)";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("  - ").append(item).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private String blankToDash(String s) {
        return s == null || s.isBlank() ? "(no summary provided)" : s;
    }
}
