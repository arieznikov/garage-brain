package com.garagebrain.api;

import com.garagebrain.analytics.TrendMetrics;
import com.garagebrain.ingestion.VehicleNotFoundException;
import com.garagebrain.persistence.AlertRepository;
import com.garagebrain.persistence.BaselineRepository;
import com.garagebrain.persistence.SessionRepository;
import com.garagebrain.persistence.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VehicleOverviewService {

    private final VehicleRepository vehicleRepository;
    private final SessionRepository sessionRepository;
    private final BaselineRepository baselineRepository;
    private final AlertRepository alertRepository;

    public VehicleOverviewService(
            VehicleRepository vehicleRepository,
            SessionRepository sessionRepository,
            BaselineRepository baselineRepository,
            AlertRepository alertRepository) {
        this.vehicleRepository = vehicleRepository;
        this.sessionRepository = sessionRepository;
        this.baselineRepository = baselineRepository;
        this.alertRepository = alertRepository;
    }

    public VehicleOverviewResponse getOverview(UUID vehicleId) {
        if (!vehicleRepository.exists(vehicleId)) {
            throw new VehicleNotFoundException(vehicleId);
        }

        int sessionCount = sessionRepository.countByVehicleId(vehicleId);
        String nickname = vehicleRepository.findNickname(vehicleId).orElse("Vehicle");

        List<VehicleOverviewResponse.AlertView> alerts = alertRepository.findByVehicleId(vehicleId).stream()
                .map(alert -> new VehicleOverviewResponse.AlertView(
                        alert.id(),
                        alert.metric(),
                        metricLabel(alert.metric()),
                        alert.pidName(),
                        alert.severity(),
                        alert.zScore()))
                .toList();

        List<VehicleOverviewResponse.BaselineView> baselines =
                baselineRepository.findByVehicleId(vehicleId).stream()
                        .map(baseline -> new VehicleOverviewResponse.BaselineView(
                                baseline.pidName(),
                                metricLabel(baseline.pidName()),
                                baseline.mean(),
                                baseline.std(),
                                baseline.sampleCount(),
                                baseline.sessionCount()))
                        .toList();

        return new VehicleOverviewResponse(
                vehicleId,
                nickname,
                sessionCount,
                new VehicleOverviewResponse.BaselineProgress(
                        sessionCount,
                        TrendMetrics.MIN_SESSIONS_FOR_ALERTS,
                        sessionCount >= TrendMetrics.MIN_SESSIONS_FOR_ALERTS),
                alerts,
                baselines);
    }

    private static String metricLabel(String metric) {
        return switch (metric) {
            case TrendMetrics.COOLANT_WARMUP_SLOPE -> "Coolant warmup slope";
            case TrendMetrics.STFT_DRIFT -> "STFT drift (cruise)";
            case TrendMetrics.LTFT_DRIFT -> "LTFT drift (cruise)";
            case TrendMetrics.BATTERY_AT_CRANK -> "Battery at crank";
            case TrendMetrics.IDLE_RPM_VARIANCE -> "Idle RPM variance";
            default -> metric;
        };
    }
}
