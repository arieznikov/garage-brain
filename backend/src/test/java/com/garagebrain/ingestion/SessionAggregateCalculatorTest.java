package com.garagebrain.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionAggregateCalculatorTest {

    @Test
    void wholeSessionAggregates_computesMeanStdMinMax() {
        List<PidSample> samples = List.of(
                new PidSample(Instant.parse("2026-06-15T10:00:00Z"), "engine_rpm", 800.0, "rpm"),
                new PidSample(Instant.parse("2026-06-15T10:00:01Z"), "engine_rpm", 900.0, "rpm"));

        List<SessionAggregateRow> rows = SessionAggregateCalculator.wholeSessionAggregates(samples);

        assertThat(rows).hasSize(1);
        SessionAggregateRow row = rows.get(0);
        assertThat(row.segment()).isEqualTo(SessionAggregateCalculator.WHOLE_SESSION_SEGMENT);
        assertThat(row.pidName()).isEqualTo("engine_rpm");
        assertThat(row.mean()).isEqualTo(850.0);
        assertThat(row.min()).isEqualTo(800.0);
        assertThat(row.max()).isEqualTo(900.0);
        assertThat(row.n()).isEqualTo(2);
        assertThat(row.std()).isGreaterThan(0.0);
    }

    @Test
    void singleSample_stdDevIsZero() {
        List<PidSample> samples =
                List.of(new PidSample(Instant.parse("2026-06-15T10:00:00Z"), "ltft_b1", 2.0, "%"));

        SessionAggregateRow row = SessionAggregateCalculator.wholeSessionAggregates(samples).get(0);

        assertThat(row.std()).isEqualTo(0.0);
        assertThat(row.mean()).isEqualTo(2.0);
    }

    @Test
    void aggregatesForSegments_groupsBySegmentName() {
        List<PidSample> idleSamples = List.of(
                new PidSample(Instant.parse("2026-06-15T10:00:00Z"), "engine_rpm", 750.0, "rpm"),
                new PidSample(Instant.parse("2026-06-15T10:00:01Z"), "engine_rpm", 770.0, "rpm"));
        List<PidSample> cruiseSamples =
                List.of(new PidSample(Instant.parse("2026-06-15T10:05:00Z"), "engine_rpm", 2200.0, "rpm"));

        List<SessionAggregateRow> rows = SessionAggregateCalculator.aggregatesForSegments(Map.of(
                "idle", idleSamples,
                "cruise", cruiseSamples));

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SessionAggregateRow::segment).containsExactlyInAnyOrder("idle", "cruise");
    }
}
