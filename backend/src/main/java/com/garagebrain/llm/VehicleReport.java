package com.garagebrain.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Structured BYOM narrative returned by Ollama (v0 schema).
 */
public record VehicleReport(
        String summary,
        String urgency,
        @JsonProperty("likely_causes") List<String> likelyCauses,
        @JsonProperty("suggested_checks") List<String> suggestedChecks,
        @JsonProperty("drive_advice") String driveAdvice) {
}
