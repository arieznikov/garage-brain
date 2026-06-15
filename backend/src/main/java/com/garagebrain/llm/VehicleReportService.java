package com.garagebrain.llm;

import com.garagebrain.api.VehicleOverviewResponse;
import com.garagebrain.api.VehicleOverviewService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VehicleReportService {

    private final VehicleOverviewService vehicleOverviewService;
    private final VehicleReportLlmClient llmClient;

    public VehicleReportService(VehicleOverviewService vehicleOverviewService, VehicleReportLlmClient llmClient) {
        this.vehicleOverviewService = vehicleOverviewService;
        this.llmClient = llmClient;
    }

    public VehicleReportResult generateReport(UUID vehicleId) {
        VehicleOverviewResponse overview = vehicleOverviewService.getOverview(vehicleId);
        VehicleReportInput input = VehicleReportInput.from(overview);

        try {
            VehicleReport report = llmClient.generateReport(input);
            return VehicleReportResult.ready(report, input);
        } catch (OllamaUnavailableException ex) {
            return VehicleReportResult.degraded(
                    ex.getMessage()
                            + " Trend metrics and alerts above are still available without the narrative.",
                    input);
        } catch (InvalidVehicleReportException ex) {
            return VehicleReportResult.degraded(
                    "Local LLM returned an invalid report after retry: "
                            + ex.getMessage()
                            + " Trend metrics and alerts above are still available.",
                    input);
        }
    }
}
