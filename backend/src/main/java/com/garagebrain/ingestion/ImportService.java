package com.garagebrain.ingestion;

import com.garagebrain.config.GarageBrainProperties;
import com.garagebrain.persistence.ImportJobRepository;
import com.garagebrain.persistence.SessionRepository;
import com.garagebrain.persistence.VehicleRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ImportService {

    private final VehicleRepository vehicleRepository;
    private final ImportJobRepository importJobRepository;
    private final SessionRepository sessionRepository;
    private final ImportProcessor importProcessor;
    private final ImportAsyncExecutor importAsyncExecutor;
    private final Path stagingDir;

    public ImportService(
            VehicleRepository vehicleRepository,
            ImportJobRepository importJobRepository,
            SessionRepository sessionRepository,
            ImportProcessor importProcessor,
            ImportAsyncExecutor importAsyncExecutor,
            GarageBrainProperties properties) {
        this.vehicleRepository = vehicleRepository;
        this.importJobRepository = importJobRepository;
        this.sessionRepository = sessionRepository;
        this.importProcessor = importProcessor;
        this.importAsyncExecutor = importAsyncExecutor;
        this.stagingDir = Path.of(properties.dataDir(), "import-staging");
    }

    public ImportJobSubmission submitImport(UUID vehicleId, byte[] csvBytes)
            throws DuplicateImportException, ImportFailedException {
        validateVehicleAndPayload(vehicleId, csvBytes);

        String contentHash = sha256Hex(csvBytes);
        rejectDuplicate(vehicleId, contentHash);

        UUID importJobId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        importJobRepository.insertRunning(importJobId, vehicleId, contentHash);

        Path stagingFile = stageCsv(importJobId, csvBytes);
        importAsyncExecutor.processStagedImport(importJobId, sessionId, vehicleId, stagingFile);

        return new ImportJobSubmission(importJobId, vehicleId, ImportJobRepository.STATUS_RUNNING);
    }

    public ImportJobStatus getImportStatus(UUID vehicleId, UUID importJobId) {
        ImportJobRepository.ImportJobRow job = importJobRepository
                .findByIdAndVehicleId(importJobId, vehicleId)
                .orElseThrow(() -> new ImportJobNotFoundException(vehicleId, importJobId));

        return switch (job.status()) {
            case ImportJobRepository.STATUS_RUNNING -> ImportJobStatus.running(
                    job.id(), job.vehicleId(), job.stage(), job.createdAt());
            case ImportJobRepository.STATUS_FAILED -> ImportJobStatus.failed(
                    job.id(), job.vehicleId(), job.error(), job.createdAt(), job.completedAt());
            case ImportJobRepository.STATUS_COMPLETED -> sessionRepository
                    .findSummaryByImportJobId(importJobId)
                    .map(session -> ImportJobStatus.completed(
                            job.id(),
                            job.vehicleId(),
                            job.createdAt(),
                            job.completedAt(),
                            new ImportResult(
                                    job.id(),
                                    session.id(),
                                    job.vehicleId(),
                                    session.source(),
                                    session.sampleCount(),
                                    session.driveStartedAt(),
                                    session.driveEndedAt(),
                                    session.parquetPath())))
                    .orElseThrow(() -> new ImportJobNotFoundException(vehicleId, importJobId));
            default -> throw new IllegalStateException("Unknown import status: " + job.status());
        };
    }

    public ImportResult importCsv(UUID vehicleId, byte[] csvBytes)
            throws DuplicateImportException, ImportFailedException {
        validateVehicleAndPayload(vehicleId, csvBytes);

        String contentHash = sha256Hex(csvBytes);
        rejectDuplicate(vehicleId, contentHash);

        UUID importJobId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        importJobRepository.insertRunning(importJobId, vehicleId, contentHash);

        return importProcessor.process(importJobId, sessionId, vehicleId, csvBytes);
    }

    private void validateVehicleAndPayload(UUID vehicleId, byte[] csvBytes) throws ImportFailedException {
        if (!vehicleRepository.exists(vehicleId)) {
            throw new ImportFailedException("Vehicle not found: " + vehicleId);
        }
        if (csvBytes == null || csvBytes.length == 0) {
            throw new ImportFailedException("Uploaded file is empty");
        }
    }

    private void rejectDuplicate(UUID vehicleId, String contentHash) throws DuplicateImportException {
        if (importJobRepository.existsCompletedForVehicleAndHash(vehicleId, contentHash)) {
            throw new DuplicateImportException(
                    "This CSV was already imported for vehicle " + vehicleId + " (content hash " + contentHash + ")");
        }
        if (importJobRepository.existsRunningForVehicleAndHash(vehicleId, contentHash)) {
            throw new DuplicateImportException("An import for this CSV is already in progress for vehicle " + vehicleId);
        }
    }

    private Path stageCsv(UUID importJobId, byte[] csvBytes) throws ImportFailedException {
        try {
            Files.createDirectories(stagingDir);
            Path stagingFile = stagingDir.resolve(importJobId + ".csv");
            Files.write(stagingFile, csvBytes);
            return stagingFile;
        } catch (IOException ex) {
            throw new ImportFailedException("Failed to stage import file", ex);
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
