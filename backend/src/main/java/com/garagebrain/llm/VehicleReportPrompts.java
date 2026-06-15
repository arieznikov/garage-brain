package com.garagebrain.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class VehicleReportPrompts {

    static final String SYSTEM =
            """
                    You are an automotive diagnostics assistant for DIY mechanics. \
                    Analyze the structured vehicle telemetry summary and respond with JSON only. \
                    Required schema:
                    {"summary": string, "urgency": "low"|"medium"|"high", \
                    "likely_causes": string[], "suggested_checks": string[], "drive_advice": string}
                    Be concise and practical. Do not invent sensor readings not present in the input.""";

    private VehicleReportPrompts() {
    }

    static String userPayload(VehicleReportInput input, ObjectMapper objectMapper) {
        try {
            return "Input:\n" + objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize report input", ex);
        }
    }

    static String ollamaPrompt(VehicleReportInput input, ObjectMapper objectMapper) {
        return SYSTEM + "\n\n" + userPayload(input, objectMapper);
    }
}
