package com.garagebrain.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.garagebrain.api.VehicleOverviewResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleReportServiceTest {

    @Mock
    private com.garagebrain.api.VehicleOverviewService vehicleOverviewService;

    @Mock
    private VehicleReportLlmClient llmClient;

    @InjectMocks
    private VehicleReportService vehicleReportService;

    @Test
    void generateReport_returnsReadyResult() {
        UUID vehicleId = UUID.randomUUID();
        VehicleOverviewResponse overview = overview(vehicleId);
        VehicleReport report = new VehicleReport(
                "Stable trends.",
                "low",
                List.of("normal wear"),
                List.of("check air filter"),
                "Drive normally.");

        when(vehicleOverviewService.getOverview(vehicleId)).thenReturn(overview);
        when(llmClient.generateReport(org.mockito.ArgumentMatchers.any())).thenReturn(report);

        VehicleReportResult result = vehicleReportService.generateReport(vehicleId);

        assertThat(result.degraded()).isFalse();
        assertThat(result.report()).isEqualTo(report);
        assertThat(result.input().vehicle().nickname()).isEqualTo("Jeep");
    }

    @Test
    void generateReport_degradedWhenOllamaUnavailable() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleOverviewService.getOverview(vehicleId)).thenReturn(overview(vehicleId));
        when(llmClient.generateReport(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new OllamaUnavailableException("connection refused"));

        VehicleReportResult result = vehicleReportService.generateReport(vehicleId);

        assertThat(result.degraded()).isTrue();
        assertThat(result.message()).contains("connection refused");
        assertThat(result.report()).isNull();
    }

    @Test
    void generateReport_degradedWhenSchemaInvalidAfterRetry() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleOverviewService.getOverview(vehicleId)).thenReturn(overview(vehicleId));
        when(llmClient.generateReport(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new InvalidVehicleReportException("missing urgency"));

        VehicleReportResult result = vehicleReportService.generateReport(vehicleId);

        assertThat(result.degraded()).isTrue();
        assertThat(result.message()).contains("Local LLM returned an invalid report");
    }

    private static VehicleOverviewResponse overview(UUID vehicleId) {
        return new VehicleOverviewResponse(
                vehicleId,
                "Jeep",
                3,
                new VehicleOverviewResponse.BaselineProgress(3, 5, false),
                List.of(),
                List.of());
    }
}
