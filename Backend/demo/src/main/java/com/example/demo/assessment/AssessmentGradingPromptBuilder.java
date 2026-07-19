package com.example.demo.assessment;

import com.example.demo.job.Job;
import org.springframework.stereotype.Component;

/**
 * Builds the system + user prompts for grading a free-text assessment answer.
 * Kept separate from the service (mirroring {@code AtsPromptBuilder}) so the
 * wording can be tuned and unit-tested without touching grading orchestration.
 */
@Component
public class AssessmentGradingPromptBuilder {

    // Guards against a pathologically long answer blowing the context window.
    private static final int MAX_ANSWER_CHARS = 8_000;

    public String systemPrompt() {
        return "You are an expert technical interviewer grading a candidate's written "
                + "answer to an assessment question. Grade objectively on correctness, "
                + "depth, and clarity. Always respond with valid JSON only, no markdown "
                + "or commentary.";
    }

    public String userPrompt(Job job, AssessmentQuestion question, String answerText) {
        String answer = answerText == null ? "" : answerText;
        if (answer.length() > MAX_ANSWER_CHARS) {
            answer = answer.substring(0, MAX_ANSWER_CHARS);
        }
        int maxScore = question.getMaxScore() != null ? question.getMaxScore() : 10;

        return """
                Job Title: %s

                Question: %s

                Candidate's Answer:
                ---
                %s
                ---

                Grade this answer from 0 to %d points, where %d means a complete,
                correct, well-articulated answer and 0 means blank or entirely wrong.
                Be objective and strict.
                Return ONLY valid JSON (no markdown, no backticks) in exactly this shape:
                {
                  "score": <0-%d>,
                  "feedback": "<1-2 sentence justification>"
                }
                """.formatted(job.getTitle(), question.getQuestionText(), answer,
                maxScore, maxScore, maxScore);
    }
}
