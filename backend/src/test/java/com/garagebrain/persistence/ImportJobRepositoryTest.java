package com.garagebrain.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.support.PostgresIntegrationTest;
import com.garagebrain.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ImportJobRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void marksImportJobFailedWithTimestamp() {
        UUID vehicleId = UUID.randomUUID();
        UUID importJobId = UUID.randomUUID();
        vehicleRepository.insert(vehicleId, "Timestamp test");

        importJobRepository.insertRunning(importJobId, vehicleId, "hash-" + importJobId);
        importJobRepository.markFailed(importJobId, "parser blew up");

        ImportJobRepository.ImportJobRow job =
                importJobRepository.findById(importJobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ImportJobRepository.STATUS_FAILED);
        assertThat(job.error()).isEqualTo("parser blew up");
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void markCompleted_clearsStageAndSetsParquetPath() {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Completed job");
        UUID importJobId = UUID.randomUUID();
        String hash = "content-hash-" + importJobId;

        importJobRepository.insertRunning(importJobId, vehicleId, hash);
        importJobRepository.updateStage(importJobId, ImportJobRepository.STAGE_WRITING_PARQUET);
        importJobRepository.markCompleted(importJobId, "/data/sessions/foo.parquet");

        ImportJobRepository.ImportJobRow job =
                importJobRepository.findById(importJobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ImportJobRepository.STATUS_COMPLETED);
        assertThat(job.stage()).isNull();
        assertThat(job.parquetPath()).isEqualTo("/data/sessions/foo.parquet");
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void dedupeQueries_detectRunningAndCompletedHashes() {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Dedupe");
        String runningHash = "running-hash";
        String completedHash = "completed-hash";

        UUID runningJobId = UUID.randomUUID();
        importJobRepository.insertRunning(runningJobId, vehicleId, runningHash);

        UUID completedJobId = UUID.randomUUID();
        importJobRepository.insertRunning(completedJobId, vehicleId, completedHash);
        importJobRepository.markCompleted(completedJobId, "/data/sessions/done.parquet");

        assertThat(importJobRepository.existsRunningForVehicleAndHash(vehicleId, runningHash))
                .isTrue();
        assertThat(importJobRepository.existsCompletedForVehicleAndHash(vehicleId, completedHash))
                .isTrue();
        assertThat(importJobRepository.existsRunningForVehicleAndHash(vehicleId, completedHash))
                .isFalse();
        assertThat(importJobRepository.existsCompletedForVehicleAndHash(vehicleId, runningHash))
                .isFalse();
    }

    @Test
    void findByIdAndVehicleId_scopesToVehicle() {
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Scope");
        UUID otherVehicleId = TestFixtures.insertVehicle(vehicleRepository, "Other");
        UUID importJobId = UUID.randomUUID();

        importJobRepository.insertRunning(importJobId, vehicleId, "hash");

        assertThat(importJobRepository.findByIdAndVehicleId(importJobId, vehicleId)).isPresent();
        assertThat(importJobRepository.findByIdAndVehicleId(importJobId, otherVehicleId))
                .isEmpty();
    }
}
