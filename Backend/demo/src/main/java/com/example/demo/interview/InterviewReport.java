package com.example.demo.interview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The JSON the interviewer model emits when the conversation is complete. The
 * model signals completion with {@code type == "INTERVIEW_COMPLETE"}; the rest
 * is the scored report. Unknown fields are ignored so minor model drift doesn't
 * break parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InterviewReport(
        @JsonProperty("type") String type,
        @JsonProperty("overall_score") int overallScore,
        @JsonProperty("strengths") List<String> strengths,
        @JsonProperty("weaknesses") List<String> weaknesses,
        @JsonProperty("recommendation") String recommendation,
        @JsonProperty("summary") String summary
) {
    /** True when this payload marks the end of the interview. */
    public boolean isComplete() {
        return "INTERVIEW_COMPLETE".equals(type);
    }
}
