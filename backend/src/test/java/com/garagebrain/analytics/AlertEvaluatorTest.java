package com.garagebrain.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garagebrain.persistence.AlertRepository;
import com.garagebrain.persistence.BaselineRepository;
import com.garagebrain.persistence.SessionAggregateRepository;
import com.garagebrain.persistence.SessionAggregateView;
import com.garagebrain.persistence.SessionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertEvaluatorTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionAggregateRepository sessionAggregateRepository;

    @Mock
    private BaselineRepository baselineRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SessionMetricExtractor metricExtractor;

    private AlertEvaluator alertEvaluator;

    @BeforeEach
    void setUp() {
        alertEvaluator = new AlertEvaluator(
                sessionRepository,
                sessionAggregateRepository,
                baselineRepository,
                alertRepository,
                metricExtractor);
    }

    @Test
    void skipsAlertsUntilMinimumSessionCount() {
        UUID vehicleId = UUID.randomUUID();
        when(sessionRepository.countByVehicleId(vehicleId)).thenReturn(4);

        assertThat(alertEvaluator.evaluateLatestSession(vehicleId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void createsLtftDriftAlertWhenZScoreExceedsThreshold() {
        UUID vehicleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(sessionRepository.countByVehicleId(vehicleId)).thenReturn(6);
        when(sessionAggregateRepository.findBySessionId(sessionId))
                .thenReturn(List.of(new SessionAggregateView(
                        TrendMetrics.SEGMENT_METRIC, TrendMetrics.LTFT_DRIFT, 2.3, 0.0, 2.3, 2.3, 1)));
        when(baselineRepository.find(vehicleId, TrendMetrics.LTFT_DRIFT))
                .thenReturn(Optional.of(new BaselineRepository.BaselineRow(
                        vehicleId, TrendMetrics.LTFT_DRIFT, 2.0, 0.05, 5, 5)));
        when(metricExtractor.pidNameForMetric(TrendMetrics.LTFT_DRIFT)).thenReturn(TrendMetrics.PID_LTFT_B1);

        List<AlertRepository.AlertRow> alerts = alertEvaluator.evaluateLatestSession(vehicleId, sessionId);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).metric()).isEqualTo(TrendMetrics.LTFT_DRIFT);
        assertThat(alerts.get(0).zScore()).isGreaterThan(TrendMetrics.Z_THRESHOLD_DRIFT);

        ArgumentCaptor<Double> zCaptor = ArgumentCaptor.forClass(Double.class);
        verify(alertRepository)
                .insert(any(), eq(vehicleId), eq(TrendMetrics.PID_LTFT_B1), eq(TrendMetrics.LTFT_DRIFT), eq(
                        TrendMetrics.SEVERITY_WARNING), zCaptor.capture());
        assertThat(zCaptor.getValue()).isGreaterThan(TrendMetrics.Z_THRESHOLD_DRIFT);
    }
}
