package com.example.demo.interview;

import com.example.demo.job.Job;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the interviewer system prompt from a job's interview configuration and
 * locates the completion JSON the model emits when done. Kept separate from the
 * service so the wording can be tuned and unit-tested in isolation (mirrors
 * {@code AtsPromptBuilder}).
 */
@Component
public class InterviewPromptBuilder {

    /**
     * System prompt that drives the whole conversation. The model asks one
     * question at a time and, after the configured number of questions, replies
     * with ONLY the INTERVIEW_COMPLETE JSON so the service can score and close.
     */
    public String systemPrompt(Job job) {
        int numQuestions = job.getInterviewNumQuestions() != null
                ? job.getInterviewNumQuestions() : 5;
        String topics = job.getInterviewTopics() == null || job.getInterviewTopics().isEmpty()
                ? "core skills relevant to the role"
                : String.join(", ", job.getInterviewTopics());

        return """
                You are a professional technical interviewer for the "%s" role.

                You will ask exactly %d questions, covering: %s.

                Rules:
                - Ask ONE question at a time. Keep each question concise.
                - After the candidate answers, either ask a brief follow-up or move
                  to the next question. Do not number the questions out loud.
                - Stay professional and encouraging. Never reveal scoring.
                - After the candidate has answered your final question, respond with
                  ONLY the following JSON and nothing else (no markdown, no prose):
                {
                  "type": "INTERVIEW_COMPLETE",
                  "overall_score": <0-100>,
                  "strengths": ["..."],
                  "weaknesses": ["..."],
                  "recommendation": "HIRE | CONSIDER | REJECT",
                  "summary": "<2-4 sentence overall assessment>"
                }

                Begin now by greeting the candidate and asking your first question.
                """.formatted(job.getTitle(), numQuestions, topics);
    }

    /**
     * Extracts the first {...} block from a raw model reply, or null if there is
     * none. LLMs often wrap JSON in ```json fences or add prose; this pulls the
     * object out so parsing is resilient (mirrors AtsService.stripToJson).
     */
    public String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return null;
    }
}
