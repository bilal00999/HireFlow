package com.example.demo.interview;

/**
 * A single message from the interviewer to the candidate, produced by
 * {@link InterviewService}. The transport layer (WebSocket today, could be REST
 * or SSE) just serializes this — it holds no transport concerns itself.
 *
 * <p>Types mirror 04-API-DESIGN.md's server-to-client messages:
 * {@code QUESTION} (a question or follow-up), {@code COMPLETE} (the interview is
 * over), and {@code ERROR} (bad token, AI unavailable, etc.).
 */
public record InterviewEvent(Type type, String content) {

    public enum Type { QUESTION, COMPLETE, ERROR }

    public static InterviewEvent question(String content) {
        return new InterviewEvent(Type.QUESTION, content);
    }

    public static InterviewEvent complete(String content) {
        return new InterviewEvent(Type.COMPLETE, content);
    }

    public static InterviewEvent error(String content) {
        return new InterviewEvent(Type.ERROR, content);
    }
}
