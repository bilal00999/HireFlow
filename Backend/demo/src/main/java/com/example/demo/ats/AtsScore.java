package com.example.demo.ats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The JSON shape we ask the LLM to return for a resume score. Unknown fields
 * are ignored so minor model drift doesn't break parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtsScore(
        @JsonProperty("overall_score") int overallScore,
        @JsonProperty("skills_match") Integer skillsMatch,
        @JsonProperty("experience_match") Integer experienceMatch,
        @JsonProperty("education_match") Integer educationMatch,
        @JsonProperty("keyword_match") Integer keywordMatch,
        @JsonProperty("matched_skills") List<String> matchedSkills,
        @JsonProperty("missing_skills") List<String> missingSkills,
        @JsonProperty("summary") String summary
) {}
