package com.garagebrain.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garagebrain.config.GarageBrainProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleClientTest {

    private MockRestServiceServer server;
    private OpenAiCompatibleClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://lmstudio.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        GarageBrainProperties properties = new GarageBrainProperties(
                "/tmp/data",
                new GarageBrainProperties.Llm(
                        "openai-compatible", "http://lmstudio.test/v1", "google/gemma-4-e2b", "lm-studio"));
        client = new OpenAiCompatibleClient(restClient, properties);
    }

    @Test
    void generateReport_parsesChatCompletionJson() {
        String validReport =
                """
                {
                  "summary": "Coolant temp stable.",
                  "urgency": "low",
                  "likely_causes": ["seasonal variance"],
                  "suggested_checks": ["verify thermostat"],
                  "drive_advice": "Normal driving is fine."
                }
                """;
        expectChatCompletion(validReport);

        VehicleReport report = client.generateReport(sampleInput());

        assertThat(report.urgency()).isEqualTo("low");
        server.verify();
    }

    @Test
    void generateReport_retriesOnceOnInvalidJson() {
        expectChatCompletion("not-json");
        String validReport =
                """
                {
                  "summary": "Retry succeeded.",
                  "urgency": "medium",
                  "likely_causes": ["vacuum leak"],
                  "suggested_checks": ["smoke test"],
                  "drive_advice": "Avoid highway speeds."
                }
                """;
        expectChatCompletion(validReport);

        VehicleReport report = client.generateReport(sampleInput());

        assertThat(report.summary()).contains("Retry");
        server.verify();
    }

    @Test
    void unwrapJson_stripsMarkdownFences() {
        String fenced =
                """
                ```json
                {"summary":"ok","urgency":"low","likely_causes":["a"],"suggested_checks":["b"],"drive_advice":"c"}
                ```
                """;
        assertThat(OpenAiCompatibleClient.unwrapJson(fenced)).startsWith("{");
    }

    @Test
    void generateReport_unconfiguredBaseUrlThrows() {
        GarageBrainProperties properties = new GarageBrainProperties(
                "/tmp", new GarageBrainProperties.Llm("openai-compatible", "", "google/gemma-4-e2b", ""));
        OpenAiCompatibleClient unconfigured = new OpenAiCompatibleClient(
                RestClient.builder().baseUrl("http://unused/v1").build(), properties);

        assertThatThrownBy(() -> unconfigured.generateReport(sampleInput()))
                .isInstanceOf(OllamaUnavailableException.class);
    }

    private void expectChatCompletion(String assistantContent) {
        String body =
                """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": %s
                    }
                  }]
                }
                """
                        .formatted(new ObjectMapper().valueToTree(assistantContent).toString());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "http://lmstudio.test/v1/chat/completions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        body, MediaType.APPLICATION_JSON));
    }

    private static VehicleReportInput sampleInput() {
        return new VehicleReportInput(
                new VehicleReportInput.Vehicle("Daily Civic"),
                new VehicleReportInput.BaselineProgress(6, 5, true),
                java.util.List.of());
    }
}
