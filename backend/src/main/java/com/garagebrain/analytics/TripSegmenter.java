package com.garagebrain.analytics;

import com.garagebrain.ingestion.PidSample;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Rule-based drive segmentation for trend metrics.
 *
 * <p>With vehicle speed: idle, cruise, and WOT are derived from speed + RPM.
 * Warmup and crank are always time windows from session start.
 * Without speed: falls back to time-only windows and RPM heuristics.
 */
@Component
public class TripSegmenter {

    public static final String SEGMENT_ALL = "all";
    public static final String SEGMENT_IDLE = "idle";
    public static final String SEGMENT_CRUISE = "cruise";
    public static final String SEGMENT_WOT = "wot";
    public static final String SEGMENT_WARMUP = "warmup";
    public static final String SEGMENT_CRANK = "crank";

    static final double IDLE_SPEED_MAX_KMH = 3.0;
    static final double CRUISE_MIN_SPEED_KMH = 35.0;
    static final double WOT_MIN_SPEED_KMH = 25.0;
    static final int WOT_MIN_RPM = 4_000;
    static final int FALLBACK_IDLE_MAX_RPM = 1_200;
    static final int FALLBACK_CRUISE_MIN_RPM = 1_500;
    static final int FALLBACK_CRUISE_MAX_RPM = 3_500;
    static final Duration WARMUP_DURATION = Duration.ofMinutes(10);
    static final Duration CRANK_DURATION = Duration.ofSeconds(30);

    private static final String PID_VEHICLE_SPEED = "vehicle_speed";
    private static final String PID_GPS_SPEED = "gps_speed";
    private static final String PID_ENGINE_RPM = "engine_rpm";

    private static final List<String> DERIVED_SEGMENTS =
            List.of(SEGMENT_IDLE, SEGMENT_CRUISE, SEGMENT_WOT, SEGMENT_WARMUP, SEGMENT_CRANK);

    public TripSegmentation segment(List<PidSample> samples, Instant driveStartedAt) {
        if (samples.isEmpty()) {
            return new TripSegmentation(Map.of(SEGMENT_ALL, List.of()), false, false);
        }

        Instant sessionStart = driveStartedAt != null
                ? driveStartedAt
                : samples.stream()
                        .map(PidSample::timestamp)
                        .min(Instant::compareTo)
                        .orElse(Instant.EPOCH);

        Map<Instant, Map<String, Double>> timeline = buildTimeline(samples);
        boolean speedAvailable = hasSpeedData(timeline);
        boolean approximated = !speedAvailable;

        Map<String, List<PidSample>> grouped = new LinkedHashMap<>();
        grouped.put(SEGMENT_ALL, new ArrayList<>(samples));
        for (String segment : DERIVED_SEGMENTS) {
            grouped.put(segment, new ArrayList<>());
        }

        for (PidSample sample : samples) {
            Duration offset = Duration.between(sessionStart, sample.timestamp());
            Map<String, Double> pidsAtTime = timeline.getOrDefault(sample.timestamp(), Map.of());
            Set<Segment> segments = classify(offset, pidsAtTime, speedAvailable);
            for (Segment segment : segments) {
                grouped.get(segment.label).add(sample);
            }
        }

        grouped.entrySet().removeIf(entry -> !SEGMENT_ALL.equals(entry.getKey()) && entry.getValue().isEmpty());
        return new TripSegmentation(Map.copyOf(grouped), speedAvailable, approximated);
    }

    private static Set<Segment> classify(Duration offset, Map<String, Double> pidsAtTime, boolean speedAvailable) {
        Set<Segment> segments = EnumSet.noneOf(Segment.class);

        if (!offset.isNegative() && offset.compareTo(CRANK_DURATION) <= 0) {
            segments.add(Segment.CRANK);
        }
        if (!offset.isNegative() && offset.compareTo(WARMUP_DURATION) <= 0) {
            segments.add(Segment.WARMUP);
        }

        if (speedAvailable) {
            double speed = speedAt(pidsAtTime);
            double rpm = rpmAt(pidsAtTime);
            if (speed <= IDLE_SPEED_MAX_KMH) {
                segments.add(Segment.IDLE);
            } else if (speed >= WOT_MIN_SPEED_KMH && rpm >= WOT_MIN_RPM) {
                segments.add(Segment.WOT);
            } else if (speed >= CRUISE_MIN_SPEED_KMH) {
                segments.add(Segment.CRUISE);
            }
            return segments;
        }

        double rpm = rpmAt(pidsAtTime);
        if (rpm > 0 && rpm <= FALLBACK_IDLE_MAX_RPM && offset.compareTo(WARMUP_DURATION) > 0) {
            segments.add(Segment.IDLE);
        } else if (offset.compareTo(WARMUP_DURATION) > 0) {
            if (rpm >= FALLBACK_CRUISE_MIN_RPM && rpm <= FALLBACK_CRUISE_MAX_RPM) {
                segments.add(Segment.CRUISE);
            } else if (rpm > FALLBACK_CRUISE_MAX_RPM) {
                segments.add(Segment.WOT);
            }
        }

        return segments;
    }

    private static Map<Instant, Map<String, Double>> buildTimeline(List<PidSample> samples) {
        Map<Instant, Map<String, Double>> timeline = new HashMap<>();
        for (PidSample sample : samples) {
            timeline.computeIfAbsent(sample.timestamp(), ignored -> new HashMap<>())
                    .put(sample.pidName(), sample.value());
        }
        return timeline;
    }

    private static boolean hasSpeedData(Map<Instant, Map<String, Double>> timeline) {
        return timeline.values().stream()
                .anyMatch(pids -> pids.containsKey(PID_VEHICLE_SPEED) || pids.containsKey(PID_GPS_SPEED));
    }

    private static double speedAt(Map<String, Double> pidsAtTime) {
        if (pidsAtTime.containsKey(PID_VEHICLE_SPEED)) {
            return pidsAtTime.get(PID_VEHICLE_SPEED);
        }
        if (pidsAtTime.containsKey(PID_GPS_SPEED)) {
            return pidsAtTime.get(PID_GPS_SPEED);
        }
        return Double.NaN;
    }

    private static double rpmAt(Map<String, Double> pidsAtTime) {
        return pidsAtTime.getOrDefault(PID_ENGINE_RPM, 0.0);
    }

    private enum Segment {
        IDLE(SEGMENT_IDLE),
        CRUISE(SEGMENT_CRUISE),
        WOT(SEGMENT_WOT),
        WARMUP(SEGMENT_WARMUP),
        CRANK(SEGMENT_CRANK);

        private final String label;

        Segment(String label) {
            this.label = label;
        }
    }

    public record TripSegmentation(
            Map<String, List<PidSample>> segments, boolean speedAvailable, boolean approximated) {
    }
}
