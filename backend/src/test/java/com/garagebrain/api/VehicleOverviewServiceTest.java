package com.garagebrain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.garagebrain.analytics.TrendMetrics;
import com.garagebrain.ingestion.VehicleNotFoundException;
import com.garagebrain.persistence.AlertRepository;
import com.garagebrain.persistence.BaselineRepository;
import com.garagebrain.persistence.SessionRepository;
import com.garagebrain.persistence.VehicleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleOverviewServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private BaselineRepository baselineRepository;

    @Mock
    private AlertRepository alertRepository;

    private VehicleOverviewService service;

    @BeforeEach
    void setUp() {
        service = new VehicleOverviewService(
                vehicleRepository, sessionRepository, baselineRepository, alertRepository);
    }

    @Test
    void getOverview_unknownVehicleThrows() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.exists(vehicleId)).thenReturn(false);

        assertThatThrownBy(() -> service.getOverview(vehicleId))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void getOverview_returnsBaselineProgress() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.exists(vehicleId)).thenReturn(true);
        when(vehicleRepository.findNickname(vehicleId)).thenReturn(Optional.of("Jeep"));
        when(sessionRepository.countByVehicleId(vehicleId)).thenReturn(2);
        when(alertRepository.findByVehicleId(vehicleId)).thenReturn(List.of());
        when(baselineRepository.findByVehicleId(vehicleId)).thenReturn(List.of());

        VehicleOverviewResponse overview = service.getOverview(vehicleId);

        assertThat(overview.vehicleId()).isEqualTo(vehicleId);
        assertThat(overview.nickname()).isEqualTo("Jeep");
        assertThat(overview.sessionCount()).isEqualTo(2);
        assertThat(overview.baselineProgress().sessions()).isEqualTo(2);
        assertThat(overview.baselineProgress().required()).isEqualTo(TrendMetrics.MIN_SESSIONS_FOR_ALERTS);
        assertThat(overview.baselineProgress().ready()).isFalse();
    }
}
