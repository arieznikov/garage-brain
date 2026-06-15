package com.garagebrain.ingestion;

import com.garagebrain.persistence.ImportJobRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ImportProcessor {

    private final ImportJobRepository importJobRepository;
    private final ImportPersistence importPersistence;
    private final CarScannerCsvParser parser;
    private final ParquetSessionWriter parquetSessionWriter;

    ImportProcessor(
            ImportJobRepository importJobRepository,
            ImportPersistence importPersistence,
            CarScannerCsvParser parser,
            ParquetSessionWriter parquetSessionWriter) {
        this.importJobRepository = importJobRepository;
        this.importPersistence = importPersistence;
        this.parser = parser;
        this.parquetSessionWriter = parquetSessionWriter;
    }

    ImportResult process(UUID importJobId, UUID sessionId, UUID vehicleId, byte[] csvBytes)
            throws ImportFailedException {
        Path parquetPath = null;
        try {
            ParsedSession parsed = parser.parse(new ByteArrayInputStream(csvBytes));
            if (parsed.samples().isEmpty()) {
                throw new ParseException("CSV contains no PID samples. " + CarScannerCsvParser.UNSUPPORTED_HINT);
            }

            importJobRepository.updateStage(importJobId, ImportJobRepository.STAGE_WRITING_PARQUET);
            parquetPath = parquetSessionWriter.write(vehicleId, sessionId, parsed.samples());

            importPersistence.persistSession(
                    importJobId, sessionId, vehicleId, parsed, parquetPath.toString());

            return new ImportResult(
                    importJobId,
                    sessionId,
                    vehicleId,
                    parsed.source(),
                    parsed.samples().size(),
                    parsed.driveStartedAt(),
                    parsed.driveEndedAt(),
                    parquetPath.toString());
        } catch (Exception ex) {
            if (parquetPath != null) {
                try {
                    Files.deleteIfExists(parquetPath);
                } catch (IOException cleanupEx) {
                    ex.addSuppressed(cleanupEx);
                }
            }
            String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            importJobRepository.markFailed(importJobId, error);
            if (ex instanceof ParseException || ex instanceof ImportFailedException) {
                throw ex instanceof ImportFailedException failed
                        ? failed
                        : new ImportFailedException(error, ex);
            }
            throw new ImportFailedException("Import failed: " + error, ex);
        }
    }
}
