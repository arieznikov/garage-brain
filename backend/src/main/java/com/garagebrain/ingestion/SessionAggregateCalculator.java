package com.garagebrain.ingestion;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class SessionAggregateCalculator {

    static final String WHOLE_SESSION_SEGMENT = "all";

    private SessionAggregateCalculator() {
    }

    static List<SessionAggregateRow> aggregatesForSegments(Map<String, List<PidSample>> samplesBySegment) {
        List<SessionAggregateRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<PidSample>> entry : samplesBySegment.entrySet()) {
            rows.addAll(aggregatesForSegment(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    static List<SessionAggregateRow> wholeSessionAggregates(List<PidSample> samples) {
        return aggregatesForSegment(WHOLE_SESSION_SEGMENT, samples);
    }

    private static List<SessionAggregateRow> aggregatesForSegment(String segment, List<PidSample> samples) {
        Map<String, List<Double>> valuesByPid = samples.stream()
                .collect(Collectors.groupingBy(
                        PidSample::pidName, Collectors.mapping(PidSample::value, Collectors.toList())));

        List<SessionAggregateRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : valuesByPid.entrySet()) {
            List<Double> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }
            DoubleSummaryStatistics stats = values.stream()
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
            rows.add(new SessionAggregateRow(
                    segment,
                    entry.getKey(),
                    stats.getAverage(),
                    stdDev(values, stats.getAverage()),
                    stats.getMin(),
                    stats.getMax(),
                    values.size()));
        }
        return rows;
    }

    private static double stdDev(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        double variance = values.stream()
                .mapToDouble(value -> {
                    double delta = value - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}
