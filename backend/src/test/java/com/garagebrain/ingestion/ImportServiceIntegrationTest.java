package com.garagebrain.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garagebrain.analytics.TrendMetrics;
import com.garagebrain.persistence.AlertRepository;
import com.garagebrain.persistence.VehicleRepository;
import com.garagebrain.support.GoldenDriftFixtures;
import com.garagebrain.support.PostgresIntegrationTest;
import com.garagebrain.support.TestFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ImportServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void importsSyntheticCsvAndPersistsSessionAggregates() throws Exception {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Test Jeep");

        byte[] csv = TestFixtures.readClasspath("fixtures/car-scanner-csv2-synthetic-warmup.csv");
        ImportResult result = importService.importCsv(vehicleId, csv);

        assertThat(result.vehicleId()).isEqualTo(vehicleId);
        assertThat(result.sampleCount()).isPositive();
        assertThat(Files.exists(Path.of(result.parquetPath()))).isTrue();

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sessions WHERE id = ?",
                Integer.class,
                result.sessionId());
        assertThat(sessionCount).isEqualTo(1);

        Integer aggregateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM session_aggregates WHERE session_id = ?",
                Integer.class,
                result.sessionId());
        assertThat(aggregateCount).isPositive();

        Integer idleAggregates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM session_aggregates WHERE session_id = ? AND segment = 'idle'",
                Integer.class,
                result.sessionId());
        assertThat(idleAggregates).isPositive();

        String jobStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM import_jobs WHERE id = ?",
                String.class,
                result.importJobId());
        assertThat(jobStatus).isEqualTo("completed");
    }

    @Test
    void raisesLtftDriftAlertAfterSixSessions() throws Exception {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Drift Test");

        for (int i = 0; i < GoldenDriftFixtures.BASELINE_LTFT.length; i++) {
            importService.importCsv(
                    vehicleId, GoldenDriftFixtures.cruiseCsv(GoldenDriftFixtures.BASELINE_LTFT[i], i));
        }

        ImportResult drift = importService.importCsv(
                vehicleId, TestFixtures.readClasspath(GoldenDriftFixtures.DRIFT_FIXTURE));

        List<AlertRepository.AlertRow> alerts = alertRepository.findByVehicleId(vehicleId);
        assertThat(alerts)
                .extracting(AlertRepository.AlertRow::metric)
                .contains(TrendMetrics.LTFT_DRIFT);
        assertThat(alerts.stream()
                        .filter(alert -> TrendMetrics.LTFT_DRIFT.equals(alert.metric()))
                        .findFirst()
                        .orElseThrow()
                        .zScore())
                .isGreaterThan(TrendMetrics.Z_THRESHOLD_DRIFT);

        Integer baselineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM baselines WHERE vehicle_id = ?",
                Integer.class,
                vehicleId);
        assertThat(baselineCount).isGreaterThanOrEqualTo(1);

        Integer metricRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM session_aggregates WHERE session_id = ? AND segment = 'metric'",
                Integer.class,
                drift.sessionId());
        assertThat(metricRows).isPositive();
    }

    @Test
    void rejectsDuplicateImportForSameVehicle() throws Exception {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Dedupe Test");

        byte[] csv = TestFixtures.readClasspath("fixtures/car-scanner-csv2-synthetic-warmup.csv");
        importService.importCsv(vehicleId, csv);

        assertThatThrownBy(() -> importService.importCsv(vehicleId, csv))
                .isInstanceOf(DuplicateImportException.class);
    }

    @Test
    void importsRealHorizontalExport() throws Exception {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Real Export");

        byte[] csv = TestFixtures.readClasspath("fixtures/exported_records_horizontal/2026-06-15 12-25-55.csv");
        ImportResult result = importService.importCsv(vehicleId, csv);

        assertThat(result.source()).isEqualTo("car-scanner-horizontal");
        assertThat(result.sampleCount()).isPositive();
    }

    @Test
    void rejectsImportForUnknownVehicle() {
        UUID vehicleId = UUID.randomUUID();
        byte[] csv = "Time,Engine RPM\n0,800\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importService.importCsv(vehicleId, csv))
                .isInstanceOf(ImportFailedException.class)
                .hasMessageContaining("Vehicle not found");
    }
}
