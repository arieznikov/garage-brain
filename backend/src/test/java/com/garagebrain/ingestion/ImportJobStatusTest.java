package com.garagebrain.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.persistence.ImportJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportJobStatusTest {

    @Test
    void runningStatusHasNoResultPayload() {
        UUID jobId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        ImportJobStatus status = ImportJobStatus.running(jobId, vehicleId, ImportJobRepository.STAGE_PARSING, createdAt);

        assertThat(status.status()).isEqualTo(ImportJobRepository.STATUS_RUNNING);
        assertThat(status.stage()).isEqualTo(ImportJobRepository.STAGE_PARSING);
        assertThat(status.result()).isNull();
        assertThat(status.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void completedStatusIncludesResult() {
        UUID jobId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        ImportResult result = new ImportResult(
                jobId, sessionId, vehicleId, "car-scanner-csv2", 42, null, null, "/tmp/session.parquet");

        ImportJobStatus status = ImportJobStatus.completed(
                jobId, vehicleId, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"), result);

        assertThat(status.status()).isEqualTo(ImportJobRepository.STATUS_COMPLETED);
        assertThat(status.result()).isEqualTo(result);
        assertThat(Duration.between(status.createdAt(), status.completedAt())).isEqualTo(Duration.ofMinutes(1));
    }
}
