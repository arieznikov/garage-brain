package com.garagebrain.ingestion;

import java.time.Instant;
import java.util.UUID;

public record ImportResult(
        UUID importJobId,
        UUID sessionId,
        UUID vehicleId,
        String source,
        int sampleCount,
        Instant driveStartedAt,
        Instant driveEndedAt,
        String parquetPath) {
}
