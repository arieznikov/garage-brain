package com.garagebrain.analytics;

import com.garagebrain.ingestion.PidSample;
import com.garagebrain.ingestion.SessionAggregateRow;
import com.garagebrain.persistence.AlertRepository;
import com.garagebrain.persistence.SessionAggregateRepository;
import com.garagebrain.persistence.SessionAggregateView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TrendEngine {

    private final BaselineService baselineService;
    private final AlertEvaluator alertEvaluator;
    private final SessionAggregateRepository sessionAggregateRepository;

    public TrendEngine(
            BaselineService baselineService,
            AlertEvaluator alertEvaluator,
            SessionAggregateRepository sessionAggregateRepository) {
        this.baselineService = baselineService;
        this.alertEvaluator = alertEvaluator;
        this.sessionAggregateRepository = sessionAggregateRepository;
    }

    public List<AlertRepository.AlertRow> processAfterImport(
            UUID vehicleId,
            UUID sessionId,
            List<SessionAggregateView> aggregates,
            List<PidSample> samples,
            Instant sessionStart) {
        List<SessionAggregateRow> metricRows =
                baselineService.materializeSessionMetrics(aggregates, samples, sessionStart);
        if (!metricRows.isEmpty()) {
            sessionAggregateRepository.insertAll(sessionId, metricRows);
        }

        List<AlertRepository.AlertRow> alerts = alertEvaluator.evaluateLatestSession(vehicleId, sessionId);
        baselineService.recomputeBaselines(vehicleId);
        return alerts;
    }
}
