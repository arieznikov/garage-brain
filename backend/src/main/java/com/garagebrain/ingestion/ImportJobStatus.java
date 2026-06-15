package com.garagebrain.ingestion;

import com.garagebrain.persistence.ImportJobRepository;
import java.time.Instant;
import java.util.UUID;

public record ImportJobStatus(
        UUID importJobId,
        UUID vehicleId,
        String status,
        String stage,
        String error,
        Instant createdAt,
        Instant completedAt,
        ImportResult result) {

    public static ImportJobStatus running(
            UUID importJobId, UUID vehicleId, String stage, Instant createdAt) {
        return new ImportJobStatus(
                importJobId, vehicleId, ImportJobRepository.STATUS_RUNNING, stage, null, createdAt, null, null);
    }

    public static ImportJobStatus completed(
            UUID importJobId, UUID vehicleId, Instant createdAt, Instant completedAt, ImportResult result) {
        return new ImportJobStatus(
                importJobId,
                vehicleId,
                ImportJobRepository.STATUS_COMPLETED,
                null,
                null,
                createdAt,
                completedAt,
                result);
    }

    public static ImportJobStatus failed(
            UUID importJobId, UUID vehicleId, String error, Instant createdAt, Instant completedAt) {
        return new ImportJobStatus(
                importJobId,
                vehicleId,
                ImportJobRepository.STATUS_FAILED,
                null,
                error,
                createdAt,
                completedAt,
                null);
    }
}
