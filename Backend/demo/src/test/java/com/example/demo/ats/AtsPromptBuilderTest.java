package com.example.demo.ats;

import com.example.demo.job.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtsPromptBuilderTest {

    private final AtsPromptBuilder builder = new AtsPromptBuilder();

    private Job job() {
        Job job = new Job();
        job.setTitle("Backend Developer");
        job.setDescription("Build REST APIs");
        job.setRequiredSkills(List.of("Java", "Spring Boot"));
        return job;
    }

    @Test
    void userPrompt_includesJobFieldsSkillsAndResume() {
        String prompt = builder.userPrompt("My resume text", job());

        assertThat(prompt)
                .contains("Backend Developer")
                .contains("Build REST APIs")
                .contains("Java, Spring Boot")
                .contains("My resume text")
                .contains("overall_score");
    }

    @Test
    void userPrompt_withNoRequiredSkills_saysNoneSpecified() {
        Job job = job();
        job.setRequiredSkills(List.of());

        assertThat(builder.userPrompt("resume", job)).contains("(none specified)");
    }

    @Test
    void userPrompt_truncatesOverlongResume() {
        String hugeResume = "x".repeat(20_000);

        String prompt = builder.userPrompt(hugeResume, job());

        // Prompt scaffolding is small; the resume must have been capped well
        // under its original length (limit is 12k chars).
        assertThat(prompt.length()).isLessThan(15_000);
    }

    @Test
    void userPrompt_handlesNullResume() {
        assertThat(builder.userPrompt(null, job())).contains("Backend Developer");
    }

    @Test
    void systemPrompt_demandsJsonOnly() {
        assertThat(builder.systemPrompt().toLowerCase()).contains("json");
    }
}
