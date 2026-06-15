package com.garagebrain.api;

import com.garagebrain.llm.VehicleReport;
import com.garagebrain.llm.VehicleReportInput;
import com.garagebrain.llm.VehicleReportResult;
import com.garagebrain.llm.VehicleReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}")
@Tag(name = "BYOM report", description = "Local Ollama narrative from structured trend data")
public class VehicleReportController {

    private final VehicleReportService vehicleReportService;

    public VehicleReportController(VehicleReportService vehicleReportService) {
        this.vehicleReportService = vehicleReportService;
    }

    @GetMapping("/report")
    @Operation(
            summary = "Generate BYOM narrative report",
            description =
                    "Sends structured alerts and baseline progress to local Ollama. Never uploads raw CSV. "
                            + "Returns a degraded response when Ollama is unreachable or returns invalid JSON.")
    public VehicleReportResponse generateReport(@PathVariable UUID vehicleId) {
        VehicleReportResult result = vehicleReportService.generateReport(vehicleId);
        return VehicleReportResponse.from(result);
    }

    public record VehicleReportResponse(
            boolean degraded,
            String message,
            VehicleReportBody report,
            VehicleReportInput input) {

        static VehicleReportResponse from(VehicleReportResult result) {
            return new VehicleReportResponse(
                    result.degraded(),
                    result.message(),
                    result.report() != null ? VehicleReportBody.from(result.report()) : null,
                    result.input());
        }
    }

    public record VehicleReportBody(
            String summary,
            String urgency,
            java.util.List<String> likelyCauses,
            java.util.List<String> suggestedChecks,
            String driveAdvice) {

        static VehicleReportBody from(VehicleReport report) {
            return new VehicleReportBody(
                    report.summary(),
                    report.urgency(),
                    report.likelyCauses(),
                    report.suggestedChecks(),
                    report.driveAdvice());
        }
    }
}
