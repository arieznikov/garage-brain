package com.garagebrain.persistence;

import com.garagebrain.ingestion.SessionAggregateRow;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionAggregateRepository {

    private final JdbcTemplate jdbcTemplate;

    public SessionAggregateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SessionAggregateView> findBySessionId(UUID sessionId) {
        return jdbcTemplate.query(
                """
                        SELECT segment, pid_name, mean, std, min, max, n
                        FROM session_aggregates
                        WHERE session_id = ?
                        """,
                (rs, rowNum) -> new SessionAggregateView(
                        rs.getString("segment"),
                        rs.getString("pid_name"),
                        rs.getDouble("mean"),
                        rs.getDouble("std"),
                        rs.getDouble("min"),
                        rs.getDouble("max"),
                        rs.getInt("n")),
                sessionId);
    }

    public void insertAll(UUID sessionId, List<SessionAggregateRow> aggregates) {
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO session_aggregates (
                            session_id, segment, pid_name, mean, std, min, max, n
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                aggregates,
                aggregates.size(),
                (ps, aggregate) -> {
                    ps.setObject(1, sessionId);
                    ps.setString(2, aggregate.segment());
                    ps.setString(3, aggregate.pidName());
                    ps.setDouble(4, aggregate.mean());
                    ps.setDouble(5, aggregate.std());
                    ps.setDouble(6, aggregate.min());
                    ps.setDouble(7, aggregate.max());
                    ps.setInt(8, aggregate.n());
                });
    }
}
