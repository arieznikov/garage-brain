package com.garagebrain.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garagebrain.config.GarageBrainProperties;
import com.garagebrain.persistence.ImportJobRepository;
import com.garagebrain.persistence.SessionRepository;
import com.garagebrain.persistence.VehicleRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ImportJobRepository importJobRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ImportProcessor importProcessor;

    @Mock
    private ImportAsyncExecutor importAsyncExecutor;

    @TempDir
    Path tempDir;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ImportService(
                vehicleRepository,
                importJobRepository,
                sessionRepository,
                importProcessor,
                importAsyncExecutor,
                new GarageBrainProperties(tempDir.toString(), new GarageBrainProperties.Llm("ollama", "", "", "")));
    }

    @Test
    void sha256Hex_isDeterministic() {
        byte[] payload = "same csv bytes".getBytes();

        String first = ImportService.sha256Hex(payload);
        String second = ImportService.sha256Hex(payload);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void importCsv_rejectsUnknownVehicle() {
        UUID vehicleId = UUID.randomUUID();
        byte[] csv = "Time,Engine RPM\n0,800\n".getBytes();

        when(vehicleRepository.exists(vehicleId)).thenReturn(false);

        assertThatThrownBy(() -> importService.importCsv(vehicleId, csv))
                .isInstanceOf(ImportFailedException.class)
                .hasMessageContaining("Vehicle not found");

        verify(importJobRepository, never()).insertRunning(any(), any(), any());
    }

    @Test
    void importCsv_rejectsEmptyPayload() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.exists(vehicleId)).thenReturn(true);

        assertThatThrownBy(() -> importService.importCsv(vehicleId, new byte[0]))
                .isInstanceOf(ImportFailedException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void importCsv_rejectsCompletedDuplicate() {
        UUID vehicleId = UUID.randomUUID();
        byte[] csv = "Time,Engine RPM\n0,800\n".getBytes();
        String hash = ImportService.sha256Hex(csv);

        when(vehicleRepository.exists(vehicleId)).thenReturn(true);
        when(importJobRepository.existsCompletedForVehicleAndHash(vehicleId, hash)).thenReturn(true);

        assertThatThrownBy(() -> importService.importCsv(vehicleId, csv))
                .isInstanceOf(DuplicateImportException.class);
    }

    @Test
    void getImportStatus_returnsRunningJob() {
        UUID vehicleId = UUID.randomUUID();
        UUID importJobId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-15T10:00:00Z");

        when(importJobRepository.findByIdAndVehicleId(importJobId, vehicleId))
                .thenReturn(Optional.of(new ImportJobRepository.ImportJobRow(
                        importJobId,
                        vehicleId,
                        ImportJobRepository.STATUS_RUNNING,
                        ImportJobRepository.STAGE_PARSING,
                        null,
                        null,
                        createdAt,
                        null)));

        ImportJobStatus status = importService.getImportStatus(vehicleId, importJobId);

        assertThat(status.status()).isEqualTo(ImportJobRepository.STATUS_RUNNING);
        assertThat(status.stage()).isEqualTo(ImportJobRepository.STAGE_PARSING);
        assertThat(status.result()).isNull();
    }

    @Test
    void getImportStatus_unknownJobThrows() {
        UUID vehicleId = UUID.randomUUID();
        UUID importJobId = UUID.randomUUID();

        when(importJobRepository.findByIdAndVehicleId(importJobId, vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importService.getImportStatus(vehicleId, importJobId))
                .isInstanceOf(ImportJobNotFoundException.class);
    }

    @Test
    void submitImport_stagesFileAndStartsAsyncProcessing() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        byte[] csv = "Time,Engine RPM\n0,800\n".getBytes();
        String hash = ImportService.sha256Hex(csv);

        when(vehicleRepository.exists(vehicleId)).thenReturn(true);
        when(importJobRepository.existsCompletedForVehicleAndHash(vehicleId, hash)).thenReturn(false);
        when(importJobRepository.existsRunningForVehicleAndHash(vehicleId, hash)).thenReturn(false);

        ImportJobSubmission submission = importService.submitImport(vehicleId, csv);

        assertThat(submission.status()).isEqualTo(ImportJobRepository.STATUS_RUNNING);
        verify(importJobRepository).insertRunning(eq(submission.importJobId()), eq(vehicleId), eq(hash));
        verify(importAsyncExecutor)
                .processStagedImport(eq(submission.importJobId()), any(), eq(vehicleId), any(Path.class));

        Path stagingDir = tempDir.resolve("import-staging");
        try (var files = Files.list(stagingDir)) {
            assertThat(files).hasSize(1);
        }
    }
}
