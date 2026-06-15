package com.garagebrain.analytics;

import com.garagebrain.ingestion.PidSample;
import com.garagebrain.ingestion.SessionAggregateRow;
import com.garagebrain.persistence.BaselineRepository;
import com.garagebrain.persistence.SessionAggregateRepository;
import com.garagebrain.persistence.SessionAggregateView;
import com.garagebrain.persistence.SessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BaselineService {

    private final SessionRepository sessionRepository;
    private final SessionAggregateRepository sessionAggregateRepository;
    private final SessionMetricExtractor metricExtractor;
    private final BaselineRepository baselineRepository;

    public BaselineService(
            SessionRepository sessionRepository,
            SessionAggregateRepository sessionAggregateRepository,
            SessionMetricExtractor metricExtractor,
            BaselineRepository baselineRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionAggregateRepository = sessionAggregateRepository;
        this.metricExtractor = metricExtractor;
        this.baselineRepository = baselineRepository;
    }

    public List<SessionAggregateRow> materializeSessionMetrics(
            List<SessionAggregateView> aggregates, List<PidSample> samples, Instant sessionStart) {
        List<SessionAggregateRow> rows = new ArrayList<>();
        for (String metric : TrendMetrics.ALL) {
            metricExtractor
                    .extract(metric, aggregates, samples, sessionStart)
                    .ifPresent(value -> rows.add(new SessionAggregateRow(
                            TrendMetrics.SEGMENT_METRIC, metric, value, 0.0, value, value, 1)));
        }
        return rows;
    }

    public void recomputeBaselines(UUID vehicleId) {
        List<UUID> sessionIds =
                sessionRepository.findRecentSessionIds(vehicleId, TrendMetrics.ROLLING_SESSION_WINDOW);

        for (String metric : TrendMetrics.ALL) {
            List<Double> values = new ArrayList<>();
            for (UUID sessionId : sessionIds) {
                metricValue(sessionId, metric).ifPresent(values::add);
            }
            if (values.isEmpty()) {
                continue;
            }

            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double std = stdDev(values, mean);
            baselineRepository.upsert(
                    vehicleId,
                    metric,
                    mean,
                    std,
                    values.size(),
                    sessionRepository.countByVehicleId(vehicleId));
        }
    }

    private Optional<Double> metricValue(UUID sessionId, String metric) {
        return sessionAggregateRepository.findBySessionId(sessionId).stream()
                .filter(row -> TrendMetrics.SEGMENT_METRIC.equals(row.segment())
                        && metric.equals(row.pidName()))
                .map(SessionAggregateView::mean)
                .findFirst();
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
