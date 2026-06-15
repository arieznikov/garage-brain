package com.garagebrain.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garagebrain.config.GarageBrainProperties;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** OpenAI-compatible chat completions (LM Studio, llama.cpp server, etc.). */
public class OpenAiCompatibleClient implements VehicleReportLlmClient {

    private final RestClient restClient;
    private final GarageBrainProperties.Llm llm;
    private final ObjectMapper objectMapper;
    private final VehicleReportParser reportParser;

    public OpenAiCompatibleClient(RestClient llmRestClient, GarageBrainProperties properties) {
        this.restClient = llmRestClient;
        this.llm = properties.llm();
        this.objectMapper = new ObjectMapper();
        this.reportParser = new VehicleReportParser(objectMapper);
    }

    @Override
    public boolean isConfigured() {
        return llm.baseUrl() != null && !llm.baseUrl().isBlank();
    }

    @Override
    public String configuredEndpoint() {
        return llm.baseUrl();
    }

    @Override
    public VehicleReport generateReport(VehicleReportInput input) {
        if (!isConfigured()) {
            throw new OllamaUnavailableException("LLM base URL is not configured");
        }
        String model = llm.model() == null || llm.model().isBlank() ? "local-model" : llm.model();
        String userPayload = VehicleReportPrompts.userPayload(input, objectMapper);

        InvalidVehicleReportException lastValidationError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String responseJson = callChatCompletions(model, userPayload);
                return reportParser.parse(responseJson);
            } catch (InvalidVehicleReportException ex) {
                lastValidationError = ex;
            }
        }
        throw lastValidationError != null
                ? lastValidationError
                : new InvalidVehicleReportException("LLM response failed schema validation");
    }

    private String callChatCompletions(String model, String userPayload) {
        // LM Studio and many local OpenAI-compatible servers reject json_object; prompt + parser handle JSON.
        ChatRequest request = new ChatRequest(
                model,
                List.of(
                        new ChatMessage("system", VehicleReportPrompts.SYSTEM),
                        new ChatMessage("user", userPayload)),
                false);
        try {
            RestClient.RequestBodySpec spec = restClient
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            if (llm.apiKey() != null && !llm.apiKey().isBlank()) {
                spec = spec.header("Authorization", "Bearer " + llm.apiKey());
            }
            ChatResponse response = spec.body(request).retrieve().body(ChatResponse.class);
            String content = extractContent(response);
            if (content.isBlank()) {
                throw new InvalidVehicleReportException("LLM returned an empty response body");
            }
            return unwrapJson(content);
        } catch (ResourceAccessException ex) {
            throw new OllamaUnavailableException(
                    "Cannot reach LLM at " + llm.baseUrl() + ": " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new OllamaUnavailableException(
                    "LLM returned HTTP " + ex.getStatusCode().value() + ": " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new OllamaUnavailableException("LLM request failed: " + ex.getMessage(), ex);
        }
    }

    private static String extractContent(ChatResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            return "";
        }
        String content = response.choices().get(0).message().content();
        return content == null ? "" : content;
    }

    /** Local models often wrap JSON in markdown fences despite the system prompt. */
    static String unwrapJson(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int bodyStart = trimmed.indexOf('\n');
        int fenceEnd = trimmed.lastIndexOf("```");
        if (bodyStart < 0 || fenceEnd <= bodyStart) {
            return trimmed;
        }
        return trimmed.substring(bodyStart + 1, fenceEnd).trim();
    }

    private record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }
}
