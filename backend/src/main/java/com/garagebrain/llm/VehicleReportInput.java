package com.garagebrain.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.garagebrain.api.VehicleOverviewResponse;
import java.util.List;
import java.util.UUID;

/** Structured JSON sent to Ollama — never raw CSV. */
public record VehicleReportInput(
        Vehicle vehicle,
        @JsonProperty("baseline_progress") BaselineProgress baselineProgress,
        List<Alert> alerts) {

    public static VehicleReportInput from(VehicleOverviewResponse overview) {
        return new VehicleReportInput(
                new Vehicle(overview.nickname()),
                new BaselineProgress(
                        overview.baselineProgress().sessions(),
                        overview.baselineProgress().required(),
                        overview.baselineProgress().ready()),
                overview.alerts().stream()
                        .map(alert -> new Alert(
                                alert.metric(),
                                alert.severity(),
                                alert.zScore(),
                                alert.pidName()))
                        .toList());
    }

    public record Vehicle(String nickname) {
    }

    public record BaselineProgress(int sessions, int required, boolean ready) {
    }

    public record Alert(String metric, String severity, @JsonProperty("z_score") double zScore, String pid) {
    }
}
