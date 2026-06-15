package com.garagebrain.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportJobRepository {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    public static final String STAGE_PARSING = "parsing";
    public static final String STAGE_WRITING_PARQUET = "writing_parquet";
    public static final String STAGE_PERSISTING = "persisting";

    private final JdbcTemplate jdbcTemplate;

    public ImportJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertRunning(UUID id, UUID vehicleId, String contentHash) {
        jdbcTemplate.update(
                """
                        INSERT INTO import_jobs (id, vehicle_id, status, stage, content_hash)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id,
                vehicleId,
                STATUS_RUNNING,
                STAGE_PARSING,
                contentHash);
    }

    public void updateStage(UUID id, String stage) {
        jdbcTemplate.update("UPDATE import_jobs SET stage = ? WHERE id = ?", stage, id);
    }

    public void markCompleted(UUID id, String parquetPath) {
        jdbcTemplate.update(
                """
                        UPDATE import_jobs
                        SET status = ?, stage = NULL, error = NULL, parquet_path = ?, completed_at = ?
                        WHERE id = ?
                        """,
                STATUS_COMPLETED,
                parquetPath,
                JdbcInstant.now(),
                id);
    }

    public void markFailed(UUID id, String error) {
        jdbcTemplate.update(
                """
                        UPDATE import_jobs
                        SET status = ?, stage = NULL, error = ?, completed_at = ?
                        WHERE id = ?
                        """,
                STATUS_FAILED,
                error,
                JdbcInstant.now(),
                id);
    }

    public boolean existsRunningForVehicleAndHash(UUID vehicleId, String contentHash) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM import_jobs
                        WHERE vehicle_id = ? AND content_hash = ? AND status = ?
                        """,
                Integer.class,
                vehicleId,
                contentHash,
                STATUS_RUNNING);
        return count != null && count > 0;
    }

    public boolean existsCompletedForVehicleAndHash(UUID vehicleId, String contentHash) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM import_jobs
                        WHERE vehicle_id = ? AND content_hash = ? AND status = ?
                        """,
                Integer.class,
                vehicleId,
                contentHash,
                STATUS_COMPLETED);
        return count != null && count > 0;
    }

    public Optional<ImportJobRow> findById(UUID id) {
        try {
            return Optional.of(queryJobRow("WHERE id = ?", id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<ImportJobRow> findByIdAndVehicleId(UUID id, UUID vehicleId) {
        try {
            return Optional.of(queryJobRow("WHERE id = ? AND vehicle_id = ?", id, vehicleId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private ImportJobRow queryJobRow(String whereClause, Object... args) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT id, vehicle_id, status, stage, error, parquet_path, created_at, completed_at
                        FROM import_jobs %s
                        """
                        .formatted(whereClause),
                (rs, rowNum) -> new ImportJobRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("vehicle_id")),
                        rs.getString("status"),
                        rs.getString("stage"),
                        rs.getString("error"),
                        rs.getString("parquet_path"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("completed_at") != null
                                ? rs.getTimestamp("completed_at").toInstant()
                                : null),
                args);
    }

    public record ImportJobRow(
            UUID id,
            UUID vehicleId,
            String status,
            String stage,
            String error,
            String parquetPath,
            Instant createdAt,
            Instant completedAt) {
    }
}
