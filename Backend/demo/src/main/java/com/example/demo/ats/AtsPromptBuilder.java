package com.example.demo.ats;

import com.example.demo.job.Job;
import org.springframework.stereotype.Component;

/**
 * Builds the system + user prompts for resume scoring. Kept separate from the
 * service so the wording can be tuned (and unit-tested) without touching the
 * orchestration logic.
 */
@Component
public class AtsPromptBuilder {

    // Guards against pathological resumes blowing the model's context window.
    private static final int MAX_RESUME_CHARS = 12_000;

    public String systemPrompt() {
        return "You are an expert technical recruiter performing ATS resume screening. "
                + "You evaluate resumes objectively against job requirements and always "
                + "respond with valid JSON only, no markdown or commentary.";
    }

    public String userPrompt(String resumeText, Job job) {
        String skills = job.getRequiredSkills() == null || job.getRequiredSkills().isEmpty()
                ? "(none specified)"
                : String.join(", ", job.getRequiredSkills());

        String resume = resumeText == null ? "" : resumeText;
        if (resume.length() > MAX_RESUME_CHARS) {
            resume = resume.substring(0, MAX_RESUME_CHARS);
        }

        return """
                Job Title: %s
                Job Description: %s
                Required Skills: %s

                Candidate Resume:
                ---
                %s
                ---

                Score this resume from 0-100 on how well it matches the required skills,
                experience, education, and keywords. Be objective and strict.
                Return ONLY valid JSON (no markdown, no backticks) in exactly this shape:
                {
                  "overall_score": <0-100>,
                  "skills_match": <0-100>,
                  "experience_match": <0-100>,
                  "education_match": <0-100>,
                  "keyword_match": <0-100>,
                  "matched_skills": ["..."],
                  "missing_skills": ["..."],
                  "summary": "<2-sentence explanation>"
                }
                """.formatted(job.getTitle(), job.getDescription(), skills, resume);
    }
}
