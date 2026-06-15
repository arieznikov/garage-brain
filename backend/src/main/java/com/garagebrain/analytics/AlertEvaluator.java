package com.garagebrain.analytics;

import com.garagebrain.persistence.AlertRepository;
import com.garagebrain.persistence.BaselineRepository;
import com.garagebrain.persistence.SessionAggregateRepository;
import com.garagebrain.persistence.SessionAggregateView;
import com.garagebrain.persistence.SessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AlertEvaluator {

    private final SessionRepository sessionRepository;
    private final SessionAggregateRepository sessionAggregateRepository;
    private final BaselineRepository baselineRepository;
    private final AlertRepository alertRepository;
    private final SessionMetricExtractor metricExtractor;

    public AlertEvaluator(
            SessionRepository sessionRepository,
            SessionAggregateRepository sessionAggregateRepository,
            BaselineRepository baselineRepository,
            AlertRepository alertRepository,
            SessionMetricExtractor metricExtractor) {
        this.sessionRepository = sessionRepository;
        this.sessionAggregateRepository = sessionAggregateRepository;
        this.baselineRepository = baselineRepository;
        this.alertRepository = alertRepository;
        this.metricExtractor = metricExtractor;
    }

    public List<AlertRepository.AlertRow> evaluateLatestSession(UUID vehicleId, UUID sessionId) {
        if (sessionRepository.countByVehicleId(vehicleId) < TrendMetrics.MIN_SESSIONS_FOR_ALERTS) {
            return List.of();
        }

        List<SessionAggregateView> aggregates = sessionAggregateRepository.findBySessionId(sessionId);
        List<AlertRepository.AlertRow> created = new ArrayList<>();

        for (String metric : TrendMetrics.ALL) {
            Optional<Double> observed = aggregates.stream()
                    .filter(row -> TrendMetrics.SEGMENT_METRIC.equals(row.segment())
                            && metric.equals(row.pidName()))
                    .map(SessionAggregateView::mean)
                    .findFirst();
            if (observed.isEmpty()) {
                continue;
            }

            Optional<BaselineRepository.BaselineRow> baseline = baselineRepository.find(vehicleId, metric);
            if (baseline.isEmpty()) {
                continue;
            }

            Optional<Double> zScore = MetricZScore.zScore(
                    metric, observed.get(), baseline.get().mean(), baseline.get().std());
            if (zScore.isEmpty() || !MetricZScore.exceedsThreshold(metric, zScore.get())) {
                continue;
            }

            UUID alertId = UUID.randomUUID();
            alertRepository.insert(
                    alertId,
                    vehicleId,
                    metricExtractor.pidNameForMetric(metric),
                    metric,
                    TrendMetrics.SEVERITY_WARNING,
                    zScore.get());
            created.add(new AlertRepository.AlertRow(
                    alertId,
                    vehicleId,
                    metricExtractor.pidNameForMetric(metric),
                    metric,
                    TrendMetrics.SEVERITY_WARNING,
                    zScore.get()));
        }

        return created;
    }
}
