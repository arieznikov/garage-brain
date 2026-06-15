package com.garagebrain.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID id, UUID vehicleId, String pidName, String metric, String severity, double zScore) {
        jdbcTemplate.update(
                """
                        INSERT INTO alerts (id, vehicle_id, pid_name, metric, severity, z_score, created_at, acknowledged)
                        VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)
                        """,
                id,
                vehicleId,
                pidName,
                metric,
                severity,
                zScore,
                JdbcInstant.now());
    }

    public List<AlertRow> findByVehicleId(UUID vehicleId) {
        return jdbcTemplate.query(
                """
                        SELECT id, vehicle_id, pid_name, metric, severity, z_score
                        FROM alerts
                        WHERE vehicle_id = ?
                        ORDER BY created_at DESC
                        """,
                (rs, rowNum) -> new AlertRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("vehicle_id")),
                        rs.getString("pid_name"),
                        rs.getString("metric"),
                        rs.getString("severity"),
                        rs.getDouble("z_score")),
                vehicleId);
    }

    public record AlertRow(
            UUID id, UUID vehicleId, String pidName, String metric, String severity, double zScore) {
    }
}
