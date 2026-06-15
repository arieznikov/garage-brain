package com.garagebrain.analytics;

import com.garagebrain.ingestion.PidSample;
import com.garagebrain.persistence.SessionAggregateView;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SessionMetricExtractor {

    public Optional<Double> extract(
            String metric,
            List<SessionAggregateView> aggregates,
            List<PidSample> samples,
            Instant sessionStart) {
        return switch (metric) {
            case TrendMetrics.COOLANT_WARMUP_SLOPE -> coolantWarmupSlope(samples, sessionStart);
            case TrendMetrics.STFT_DRIFT -> aggregateMean(
                    aggregates, TripSegmenter.SEGMENT_CRUISE, TrendMetrics.PID_STFT_B1);
            case TrendMetrics.LTFT_DRIFT -> aggregateMean(
                    aggregates, TripSegmenter.SEGMENT_CRUISE, TrendMetrics.PID_LTFT_B1);
            case TrendMetrics.BATTERY_AT_CRANK -> aggregateMin(
                    aggregates, TripSegmenter.SEGMENT_CRANK, TrendMetrics.PID_BATTERY_VOLTAGE);
            case TrendMetrics.IDLE_RPM_VARIANCE -> aggregateStd(
                    aggregates, TripSegmenter.SEGMENT_IDLE, TrendMetrics.PID_ENGINE_RPM);
            default -> Optional.empty();
        };
    }

    public String pidNameForMetric(String metric) {
        return switch (metric) {
            case TrendMetrics.COOLANT_WARMUP_SLOPE -> TrendMetrics.PID_COOLANT_TEMP;
            case TrendMetrics.STFT_DRIFT -> TrendMetrics.PID_STFT_B1;
            case TrendMetrics.LTFT_DRIFT -> TrendMetrics.PID_LTFT_B1;
            case TrendMetrics.BATTERY_AT_CRANK -> TrendMetrics.PID_BATTERY_VOLTAGE;
            case TrendMetrics.IDLE_RPM_VARIANCE -> TrendMetrics.PID_ENGINE_RPM;
            default -> metric;
        };
    }

    private static Optional<Double> aggregateMean(
            List<SessionAggregateView> aggregates, String segment, String pidName) {
        return aggregates.stream()
                .filter(row -> segment.equals(row.segment()) && pidName.equals(row.pidName()))
                .map(SessionAggregateView::mean)
                .findFirst();
    }

    private static Optional<Double> aggregateMin(
            List<SessionAggregateView> aggregates, String segment, String pidName) {
        return aggregates.stream()
                .filter(row -> segment.equals(row.segment()) && pidName.equals(row.pidName()))
                .map(SessionAggregateView::min)
                .findFirst();
    }

    private static Optional<Double> aggregateStd(
            List<SessionAggregateView> aggregates, String segment, String pidName) {
        return aggregates.stream()
                .filter(row -> segment.equals(row.segment()) && pidName.equals(row.pidName()))
                .map(SessionAggregateView::std)
                .findFirst();
    }

    private static Optional<Double> coolantWarmupSlope(List<PidSample> samples, Instant sessionStart) {
        Instant start = sessionStart != null ? sessionStart : earliestTimestamp(samples);
        if (start == null) {
            return Optional.empty();
        }

        List<PidSample> coolantSamples = samples.stream()
                .filter(sample -> TrendMetrics.PID_COOLANT_TEMP.equals(sample.pidName()))
                .filter(sample -> !Duration.between(start, sample.timestamp()).isNegative()
                        && Duration.between(start, sample.timestamp()).compareTo(TripSegmenter.WARMUP_DURATION) <= 0)
                .sorted(Comparator.comparing(PidSample::timestamp))
                .toList();

        if (coolantSamples.size() < 2) {
            return Optional.empty();
        }

        double meanX = 0;
        double meanY = 0;
        for (PidSample sample : coolantSamples) {
            double x = secondsFromStart(start, sample.timestamp());
            meanX += x;
            meanY += sample.value();
        }
        meanX /= coolantSamples.size();
        meanY /= coolantSamples.size();

        double covariance = 0;
        double varianceX = 0;
        for (PidSample sample : coolantSamples) {
            double x = secondsFromStart(start, sample.timestamp());
            double xDelta = x - meanX;
            double yDelta = sample.value() - meanY;
            covariance += xDelta * yDelta;
            varianceX += xDelta * xDelta;
        }

        if (varianceX == 0) {
            return Optional.empty();
        }
        return Optional.of(covariance / varianceX);
    }

    private static Instant earliestTimestamp(List<PidSample> samples) {
        return samples.stream().map(PidSample::timestamp).min(Instant::compareTo).orElse(null);
    }

    private static double secondsFromStart(Instant start, Instant timestamp) {
        return Duration.between(start, timestamp).toMillis() / 1000.0;
    }
}
