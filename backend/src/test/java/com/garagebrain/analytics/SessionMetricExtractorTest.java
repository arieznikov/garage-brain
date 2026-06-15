package com.garagebrain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.ingestion.PidSample;
import com.garagebrain.persistence.SessionAggregateView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionMetricExtractorTest {

    private final SessionMetricExtractor extractor = new SessionMetricExtractor();

    @Test
    void readsCruiseLtftMeanFromAggregates() {
        List<SessionAggregateView> aggregates = List.of(
                new SessionAggregateView(TripSegmenter.SEGMENT_CRUISE, TrendMetrics.PID_LTFT_B1, 2.0, 0.1, 1.8, 2.2, 5));

        assertThat(extractor.extract(TrendMetrics.LTFT_DRIFT, aggregates, List.of(), Instant.EPOCH))
                .contains(2.0);
    }

    @Test
    void computesCoolantWarmupSlopeFromSamples() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<PidSample> samples = List.of(
                new PidSample(start, TrendMetrics.PID_COOLANT_TEMP, 20.0, "C"),
                new PidSample(start.plusSeconds(60), TrendMetrics.PID_COOLANT_TEMP, 50.0, "C"),
                new PidSample(start.plusSeconds(120), TrendMetrics.PID_COOLANT_TEMP, 80.0, "C"));

        assertThat(extractor.extract(TrendMetrics.COOLANT_WARMUP_SLOPE, List.of(), samples, start))
                .isPresent()
                .get()
                .satisfies(slope -> assertThat(slope).isGreaterThan(0.2));
    }
}
