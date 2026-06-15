package com.garagebrain.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garagebrain.config.GarageBrainProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class OllamaClient implements VehicleReportLlmClient {

    private final RestClient restClient;
    private final GarageBrainProperties.Llm llm;
    private final ObjectMapper objectMapper;
    private final VehicleReportParser reportParser;

    public OllamaClient(RestClient llmRestClient, GarageBrainProperties properties) {
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
        String model = llm.model() == null || llm.model().isBlank() ? "llama3.2" : llm.model();
        String prompt = VehicleReportPrompts.ollamaPrompt(input, objectMapper);

        InvalidVehicleReportException lastValidationError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String responseJson = callGenerate(model, prompt);
                return reportParser.parse(responseJson);
            } catch (InvalidVehicleReportException ex) {
                lastValidationError = ex;
            }
        }
        throw lastValidationError != null
                ? lastValidationError
                : new InvalidVehicleReportException("LLM response failed schema validation");
    }

    private String callGenerate(String model, String prompt) {
        OllamaGenerateRequest request = new OllamaGenerateRequest(model, prompt, false, "json");
        try {
            OllamaGenerateResponse response = restClient
                    .post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaGenerateResponse.class);
            if (response == null || response.response() == null || response.response().isBlank()) {
                throw new InvalidVehicleReportException("LLM returned an empty response body");
            }
            return response.response().trim();
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

    private record OllamaGenerateRequest(String model, String prompt, boolean stream, String format) {
    }

    private record OllamaGenerateResponse(String response) {
    }
}
