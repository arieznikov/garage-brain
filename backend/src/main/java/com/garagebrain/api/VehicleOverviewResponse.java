package com.garagebrain.api;

import java.util.List;
import java.util.UUID;

public record VehicleOverviewResponse(
        UUID vehicleId,
        String nickname,
        int sessionCount,
        BaselineProgress baselineProgress,
        List<AlertView> alerts,
        List<BaselineView> baselines) {

    public record BaselineProgress(int sessions, int required, boolean ready) {
    }

    public record AlertView(
            UUID id, String metric, String metricLabel, String pidName, String severity, double zScore) {
    }

    public record BaselineView(
            String metric,
            String metricLabel,
            double mean,
            double std,
            int sampleCount,
            int sessionCount) {
    }
}
