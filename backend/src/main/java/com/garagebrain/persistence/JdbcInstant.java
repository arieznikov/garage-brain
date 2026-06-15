package com.garagebrain.persistence;

import java.sql.Timestamp;
import java.time.Instant;

final class JdbcInstant {

    private JdbcInstant() {}

    static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
