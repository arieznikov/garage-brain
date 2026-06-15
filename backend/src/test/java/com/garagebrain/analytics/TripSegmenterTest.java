package com.garagebrain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.ingestion.CarScannerCsvParser;
import com.garagebrain.ingestion.ParseException;
import com.garagebrain.ingestion.ParsedSession;
import com.garagebrain.ingestion.PidCanonicalMap;
import com.garagebrain.ingestion.PidSample;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TripSegmenterTest {

    private static final Instant SESSION_START = Instant.parse("2026-01-01T12:00:00Z");

    private TripSegmenter segmenter;
    private CarScannerCsvParser parser;

    @BeforeEach
    void setUp() {
        segmenter = new TripSegmenter();
        parser = new CarScannerCsvParser(new PidCanonicalMap());
    }

    @Test
    void classifiesIdleWhenSpeedIsZero() {
        TripSegmenter.TripSegmentation segmentation = segmenter.segment(
                samplesAt(
                        sample(0, "vehicle_speed", 0),
                        sample(0, "engine_rpm", 850),
                        sample(1, "vehicle_speed", 0),
                        sample(1, "engine_rpm", 820)),
                SESSION_START);

        assertThat(segmentation.speedAvailable()).isTrue();
        assertThat(segmentation.approximated()).isFalse();
        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_IDLE);
        assertThat(uniqueTimestamps(segmentation.segments().get(TripSegmenter.SEGMENT_IDLE)))
                .containsExactly(SESSION_START, SESSION_START.plusSeconds(1));
    }

    @Test
    void classifiesCruiseAtSteadyHighwaySpeed() throws Exception {
        ParsedSession parsed = parseFixture("fixtures/car-scanner-csv2-synthetic-cruise.csv");
        TripSegmenter.TripSegmentation segmentation =
                segmenter.segment(parsed.samples(), parsed.driveStartedAt());

        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_CRUISE);
        assertThat(segmentation.segments().get(TripSegmenter.SEGMENT_CRUISE)).isNotEmpty();
        assertThat(segmentation.segments()).doesNotContainKey(TripSegmenter.SEGMENT_IDLE);
    }

    @Test
    void classifiesWotAtHighRpmAndSpeed() {
        TripSegmenter.TripSegmentation segmentation = segmenter.segment(
                samplesAt(
                        sample(0, "vehicle_speed", 80),
                        sample(0, "engine_rpm", 5200),
                        sample(1, "vehicle_speed", 85),
                        sample(1, "engine_rpm", 5400)),
                SESSION_START);

        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_WOT);
        assertThat(segmentation.segments()).doesNotContainKey(TripSegmenter.SEGMENT_CRUISE);
    }

    @Test
    void warmupIncludesOnlyFirstTenMinutes() {
        TripSegmenter.TripSegmentation segmentation = segmenter.segment(
                samplesAt(
                        sample(0, "vehicle_speed", 0),
                        sampleMinutes(5, "engine_coolant_temp", 40),
                        sampleMinutes(10, "engine_coolant_temp", 70),
                        sampleMinutes(11, "engine_coolant_temp", 75)),
                SESSION_START);

        List<Instant> warmupTimes = uniqueTimestamps(segmentation.segments().get(TripSegmenter.SEGMENT_WARMUP));
        assertThat(warmupTimes)
                .contains(
                        SESSION_START,
                        SESSION_START.plus(Duration.ofMinutes(5)),
                        SESSION_START.plus(Duration.ofMinutes(10)))
                .doesNotContain(SESSION_START.plus(Duration.ofMinutes(11)));
    }

    @Test
    void crankIncludesOnlyFirstThirtySeconds() {
        TripSegmenter.TripSegmentation segmentation = segmenter.segment(
                samplesAt(
                        sample(0, "battery_voltage", 14.2),
                        sample(20, "battery_voltage", 14.1),
                        sample(45, "battery_voltage", 14.0)),
                SESSION_START);

        List<Instant> crankTimes = uniqueTimestamps(segmentation.segments().get(TripSegmenter.SEGMENT_CRANK));
        assertThat(crankTimes)
                .contains(SESSION_START, SESSION_START.plusSeconds(20))
                .doesNotContain(SESSION_START.plusSeconds(45));
    }

    @Test
    void fallsBackToTimeAndRpmWhenSpeedMissing() {
        TripSegmenter.TripSegmentation segmentation = segmenter.segment(
                samplesAt(
                        sample(0, "engine_rpm", 900),
                        sampleMinutes(11, "engine_rpm", 1100),
                        sampleMinutes(12, "engine_rpm", 2200),
                        sampleMinutes(13, "engine_rpm", 5000)),
                SESSION_START);

        assertThat(segmentation.speedAvailable()).isFalse();
        assertThat(segmentation.approximated()).isTrue();
        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_WARMUP);
        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_IDLE);
        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_CRUISE);
        assertThat(segmentation.segments()).containsKey(TripSegmenter.SEGMENT_WOT);
        assertThat(uniqueTimestamps(segmentation.segments().get(TripSegmenter.SEGMENT_IDLE)))
                .contains(SESSION_START.plus(Duration.ofMinutes(11)));
        assertThat(uniqueTimestamps(segmentation.segments().get(TripSegmenter.SEGMENT_CRUISE)))
                .contains(SESSION_START.plus(Duration.ofMinutes(12)));
        assertThat(uniqueTimestamps(segmentation.segments().get(TripSegmenter.SEGMENT_WOT)))
                .contains(SESSION_START.plus(Duration.ofMinutes(13)));
    }

    @Test
    void syntheticWarmupFixtureIncludesWarmupAndIdleSegments() throws Exception {
        ParsedSession parsed = parseFixture("fixtures/car-scanner-csv2-synthetic-warmup.csv");
        TripSegmenter.TripSegmentation segmentation =
                segmenter.segment(parsed.samples(), parsed.driveStartedAt());

        assertThat(segmentation.segments()).containsKeys(
                TripSegmenter.SEGMENT_ALL,
                TripSegmenter.SEGMENT_WARMUP,
                TripSegmenter.SEGMENT_CRANK,
                TripSegmenter.SEGMENT_IDLE);
    }

    private static List<Instant> uniqueTimestamps(List<PidSample> samples) {
        return samples.stream().map(PidSample::timestamp).distinct().sorted().toList();
    }

    private static List<PidSample> samplesAt(PidSample... samples) {
        return List.of(samples);
    }

    private static PidSample sample(long offsetSeconds, String pidName, double value) {
        return new PidSample(SESSION_START.plusSeconds(offsetSeconds), pidName, value, "");
    }

    private static PidSample sampleMinutes(long offsetMinutes, String pidName, double value) {
        return new PidSample(SESSION_START.plus(Duration.ofMinutes(offsetMinutes)), pidName, value, "");
    }

    private ParsedSession parseFixture(String resourcePath) throws IOException, ParseException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing fixture: " + resourcePath);
            }
            return parser.parse(inputStream);
        }
    }
}
