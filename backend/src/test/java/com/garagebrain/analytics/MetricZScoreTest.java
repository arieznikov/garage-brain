package com.garagebrain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricZScoreTest {

    @Test
    void flagsLtftDriftAboveThreshold() {
        double z = MetricZScore.zScore(TrendMetrics.LTFT_DRIFT, 2.3, 2.0, 0.05).orElseThrow();
        assertThat(z).isGreaterThan(TrendMetrics.Z_THRESHOLD_DRIFT);
        assertThat(MetricZScore.exceedsThreshold(TrendMetrics.LTFT_DRIFT, z)).isTrue();
    }

    @Test
    void ignoresLtftWithinBaselineBand() {
        double z = MetricZScore.zScore(TrendMetrics.LTFT_DRIFT, 2.05, 2.0, 0.1).orElseThrow();
        assertThat(MetricZScore.exceedsThreshold(TrendMetrics.LTFT_DRIFT, z)).isFalse();
    }

    @Test
    void flagsCoolantSlopeWhenObservedIsLower() {
        double z = MetricZScore.zScore(TrendMetrics.COOLANT_WARMUP_SLOPE, 0.05, 0.2, 0.05).orElseThrow();
        assertThat(MetricZScore.exceedsThreshold(TrendMetrics.COOLANT_WARMUP_SLOPE, z)).isTrue();
    }

    @Test
    void returnsEmptyWhenBaselineStdIsZero() {
        assertThat(MetricZScore.zScore(TrendMetrics.LTFT_DRIFT, 2.3, 2.0, 0.0)).isEmpty();
    }
}
