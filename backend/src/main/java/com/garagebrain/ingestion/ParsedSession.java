package com.garagebrain.ingestion;

import java.time.Instant;
import java.util.List;

public record ParsedSession(
        String source,
        Instant driveStartedAt,
        Instant driveEndedAt,
        List<PidSample> samples) {
}
