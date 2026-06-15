package com.garagebrain.analytics;

import java.util.Optional;

final class MetricZScore {

    private MetricZScore() {
    }

    static Optional<Double> zScore(String metric, double observed, double baselineMean, double baselineStd) {
        if (baselineStd <= 0) {
            return Optional.empty();
        }

        return switch (metric) {
            case TrendMetrics.COOLANT_WARMUP_SLOPE, TrendMetrics.BATTERY_AT_CRANK -> {
                double delta = baselineMean - observed;
                yield delta > 0 ? Optional.of(delta / baselineStd) : Optional.empty();
            }
            case TrendMetrics.IDLE_RPM_VARIANCE -> {
                double delta = observed - baselineMean;
                yield delta > 0 ? Optional.of(delta / baselineStd) : Optional.empty();
            }
            case TrendMetrics.STFT_DRIFT, TrendMetrics.LTFT_DRIFT ->
                    Optional.of(Math.abs(observed - baselineMean) / baselineStd);
            default -> Optional.empty();
        };
    }

    static boolean exceedsThreshold(String metric, double zScore) {
        return switch (metric) {
            case TrendMetrics.COOLANT_WARMUP_SLOPE, TrendMetrics.BATTERY_AT_CRANK ->
                    zScore >= TrendMetrics.Z_THRESHOLD_LOWER_IS_WORSE;
            case TrendMetrics.STFT_DRIFT, TrendMetrics.LTFT_DRIFT, TrendMetrics.IDLE_RPM_VARIANCE ->
                    zScore >= TrendMetrics.Z_THRESHOLD_DRIFT;
            default -> false;
        };
    }
}
