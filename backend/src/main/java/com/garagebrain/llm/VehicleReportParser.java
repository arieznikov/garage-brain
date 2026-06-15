package com.garagebrain.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VehicleReportParser {

    private static final Set<String> ALLOWED_URGENCY = Set.of("low", "medium", "high");

    private final ObjectMapper objectMapper;

    VehicleReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    VehicleReport parse(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new InvalidVehicleReportException("LLM response is not valid JSON", ex);
        }
        return validate(root);
    }

    private VehicleReport validate(JsonNode root) {
        String summary = requiredText(root, "summary");
        String urgency = requiredUrgency(root, "urgency");
        List<String> likelyCauses = requiredStringList(root, "likely_causes");
        List<String> suggestedChecks = requiredStringList(root, "suggested_checks");
        String driveAdvice = requiredText(root, "drive_advice");
        return new VehicleReport(summary, urgency, likelyCauses, suggestedChecks, driveAdvice);
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw new InvalidVehicleReportException("Missing or empty field: " + field);
        }
        return node.asText().trim();
    }

    private static String requiredUrgency(JsonNode root, String field) {
        String urgency = requiredText(root, field).toLowerCase(Locale.ROOT);
        if (!ALLOWED_URGENCY.contains(urgency)) {
            throw new InvalidVehicleReportException(
                    "Invalid urgency '" + urgency + "' — expected low, medium, or high");
        }
        return urgency;
    }

    private static List<String> requiredStringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new InvalidVehicleReportException("Missing or empty array field: " + field);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new InvalidVehicleReportException("Invalid entry in array field: " + field);
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }
}
