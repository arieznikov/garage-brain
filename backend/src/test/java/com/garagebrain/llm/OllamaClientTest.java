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

class OllamaClientTest {

    private MockRestServiceServer server;
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        GarageBrainProperties properties = new GarageBrainProperties(
                "/tmp/data", new GarageBrainProperties.Llm("ollama", "http://ollama.test", "test-model", ""));
        client = new OllamaClient(restClient, properties);
    }

    @Test
    void generateReport_parsesValidJsonResponse() {
        String validReport =
                """
                {
                  "summary": "LTFT is drifting high during cruise.",
                  "urgency": "medium",
                  "likely_causes": ["vacuum leak", "dirty MAF"],
                  "suggested_checks": ["smoke test intake", "clean MAF"],
                  "drive_advice": "Avoid hard acceleration until checked."
                }
                """;
        expectGenerate(validReport);

        VehicleReportInput input = sampleInput();
        VehicleReport report = client.generateReport(input);

        assertThat(report.summary()).contains("LTFT");
        assertThat(report.urgency()).isEqualTo("medium");
        assertThat(report.likelyCauses()).hasSize(2);
        server.verify();
    }

    @Test
    void generateReport_retriesOnceOnInvalidJson() {
        expectGenerate("{not-json");
        String validReport =
                """
                {
                  "summary": "All clear after retry.",
                  "urgency": "low",
                  "likely_causes": ["normal variance"],
                  "suggested_checks": ["monitor next drive"],
                  "drive_advice": "Continue normal driving."
                }
                """;
        expectGenerate(validReport);

        VehicleReport report = client.generateReport(sampleInput());

        assertThat(report.urgency()).isEqualTo("low");
        server.verify();
    }

    @Test
    void generateReport_failsAfterTwoInvalidResponses() {
        expectGenerate("{\"summary\":\"only field\"}");
        expectGenerate("{\"summary\":\"still invalid\"}");

        assertThatThrownBy(() -> client.generateReport(sampleInput()))
                .isInstanceOf(InvalidVehicleReportException.class);
        server.verify();
    }

    @Test
    void generateReport_unconfiguredBaseUrlThrows() {
        GarageBrainProperties properties =
                new GarageBrainProperties("/tmp", new GarageBrainProperties.Llm("ollama", "", "test-model", ""));
        OllamaClient unconfigured = new OllamaClient(
                RestClient.builder().baseUrl("http://unused").build(), properties);

        assertThatThrownBy(() -> unconfigured.generateReport(sampleInput()))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    private void expectGenerate(String responseBody) {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "http://ollama.test/api/generate"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"response\":" + new ObjectMapper().valueToTree(responseBody).toString() + "}",
                        MediaType.APPLICATION_JSON));
    }

    private static VehicleReportInput sampleInput() {
        return new VehicleReportInput(
                new VehicleReportInput.Vehicle("Daily Civic"),
                new VehicleReportInput.BaselineProgress(6, 5, true),
                java.util.List.of(new VehicleReportInput.Alert("ltft_drift", "warning", 2.8, "LTFT B1")));
    }
}
