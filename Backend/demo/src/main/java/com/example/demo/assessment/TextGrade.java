package com.example.demo.assessment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The JSON shape we ask the LLM to return when grading a free-text answer.
 * Unknown fields are ignored so minor model drift doesn't break parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextGrade(
        @JsonProperty("score") int score,
        @JsonProperty("feedback") String feedback
) {}
