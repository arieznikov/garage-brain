package com.garagebrain.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countByVehicleId(UUID vehicleId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sessions WHERE vehicle_id = ?",
                Integer.class,
                vehicleId);
        return count != null ? count : 0;
    }

    public List<UUID> findRecentSessionIds(UUID vehicleId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id FROM sessions
                        WHERE vehicle_id = ?
                        ORDER BY imported_at DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                vehicleId,
                limit);
    }

    public Instant findDriveStartedAt(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT drive_started_at FROM sessions WHERE id = ?",
                Instant.class,
                sessionId);
    }

    public Optional<SessionSummary> findSummaryByImportJobId(UUID importJobId) {
        List<SessionSummary> rows = jdbcTemplate.query(
                """
                        SELECT id, source, sample_count, drive_started_at, drive_ended_at, parquet_path
                        FROM sessions WHERE import_job_id = ?
                        LIMIT 1
                        """,
                (rs, rowNum) -> new SessionSummary(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("source"),
                        rs.getInt("sample_count"),
                        rs.getTimestamp("drive_started_at") != null
                                ? rs.getTimestamp("drive_started_at").toInstant()
                                : null,
                        rs.getTimestamp("drive_ended_at") != null
                                ? rs.getTimestamp("drive_ended_at").toInstant()
                                : null,
                        rs.getString("parquet_path")),
                importJobId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record SessionSummary(
            UUID id,
            String source,
            int sampleCount,
            Instant driveStartedAt,
            Instant driveEndedAt,
            String parquetPath) {
    }

    public void insert(
            UUID id,
            UUID vehicleId,
            UUID importJobId,
            String source,
            Instant driveStartedAt,
            Instant driveEndedAt,
            int sampleCount,
            String parquetPath) {
        jdbcTemplate.update(
                """
                        INSERT INTO sessions (
                            id, vehicle_id, import_job_id, source,
                            drive_started_at, drive_ended_at, sample_count, parquet_path
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                vehicleId,
                importJobId,
                source,
                JdbcInstant.toTimestamp(driveStartedAt),
                JdbcInstant.toTimestamp(driveEndedAt),
                sampleCount,
                parquetPath);
    }
}
