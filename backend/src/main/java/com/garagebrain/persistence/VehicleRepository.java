package com.garagebrain.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VehicleRepository {

    private final JdbcTemplate jdbcTemplate;

    public VehicleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID id, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO vehicles (id, nickname) VALUES (?, ?)",
                id,
                nickname);
    }

    public boolean exists(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicles WHERE id = ?",
                Integer.class,
                id);
        return count != null && count > 0;
    }

    public Optional<String> findNickname(UUID id) {
        try {
            String nickname = jdbcTemplate.queryForObject(
                    "SELECT nickname FROM vehicles WHERE id = ?",
                    String.class,
                    id);
            return Optional.ofNullable(nickname);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
