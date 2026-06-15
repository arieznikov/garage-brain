package com.garagebrain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}")
@Tag(name = "Vehicle overview", description = "Baseline progress, alerts, and trend baselines for the UI")
public class VehicleOverviewController {

    private final VehicleOverviewService vehicleOverviewService;

    public VehicleOverviewController(VehicleOverviewService vehicleOverviewService) {
        this.vehicleOverviewService = vehicleOverviewService;
    }

    @GetMapping("/overview")
    @Operation(
            summary = "Vehicle dashboard data",
            description = "Session count, baseline cold-start progress, active alerts, and materialized baselines.")
    public VehicleOverviewResponse getOverview(@PathVariable UUID vehicleId) {
        return vehicleOverviewService.getOverview(vehicleId);
    }
}
