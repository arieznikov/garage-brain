package com.garagebrain.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BaselineRepository {

    private final JdbcTemplate jdbcTemplate;

    public BaselineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(
            UUID vehicleId, String pidName, double mean, double std, int sampleCount, int sessionCount) {
        jdbcTemplate.update(
                """
                        INSERT INTO baselines (vehicle_id, pid_name, mean, std, sample_count, session_count, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (vehicle_id, pid_name) DO UPDATE SET
                            mean = EXCLUDED.mean,
                            std = EXCLUDED.std,
                            sample_count = EXCLUDED.sample_count,
                            session_count = EXCLUDED.session_count,
                            updated_at = EXCLUDED.updated_at
                        """,
                vehicleId,
                pidName,
                mean,
                std,
                sampleCount,
                sessionCount,
                JdbcInstant.now());
    }

    public List<BaselineRow> findByVehicleId(UUID vehicleId) {
        return jdbcTemplate.query(
                """
                        SELECT vehicle_id, pid_name, mean, std, sample_count, session_count
                        FROM baselines
                        WHERE vehicle_id = ?
                        ORDER BY pid_name
                        """,
                (rs, rowNum) -> new BaselineRow(
                        UUID.fromString(rs.getString("vehicle_id")),
                        rs.getString("pid_name"),
                        rs.getDouble("mean"),
                        rs.getDouble("std"),
                        rs.getInt("sample_count"),
                        rs.getInt("session_count")),
                vehicleId);
    }

    public Optional<BaselineRow> find(UUID vehicleId, String pidName) {
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    """
                            SELECT vehicle_id, pid_name, mean, std, sample_count, session_count
                            FROM baselines WHERE vehicle_id = ? AND pid_name = ?
                            """,
                    (rs, rowNum) -> new BaselineRow(
                            UUID.fromString(rs.getString("vehicle_id")),
                            rs.getString("pid_name"),
                            rs.getDouble("mean"),
                            rs.getDouble("std"),
                            rs.getInt("sample_count"),
                            rs.getInt("session_count")),
                    vehicleId,
                    pidName));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public record BaselineRow(
            UUID vehicleId, String pidName, double mean, double std, int sampleCount, int sessionCount) {
    }
}
