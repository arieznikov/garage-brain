package com.garagebrain.ingestion;

import com.garagebrain.persistence.ImportJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
class ImportAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(ImportAsyncExecutor.class);

    private final ImportProcessor importProcessor;
    private final ImportJobRepository importJobRepository;

    ImportAsyncExecutor(ImportProcessor importProcessor, ImportJobRepository importJobRepository) {
        this.importProcessor = importProcessor;
        this.importJobRepository = importJobRepository;
    }

    @Async("importTaskExecutor")
    public void processStagedImport(UUID importJobId, UUID sessionId, UUID vehicleId, Path stagingFile) {
        try {
            byte[] csvBytes = Files.readAllBytes(stagingFile);
            importProcessor.process(importJobId, sessionId, vehicleId, csvBytes);
        } catch (ImportFailedException ex) {
            log.debug("Import job {} failed: {}", importJobId, ex.getMessage());
        } catch (IOException ex) {
            log.warn("Failed to read staged import {}", importJobId, ex);
            markFailedSafely(importJobId, "Failed to read staged import file");
        } catch (Exception ex) {
            log.error("Unexpected failure processing import {}", importJobId, ex);
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            markFailedSafely(importJobId, "Import failed: " + message);
        } finally {
            try {
                Files.deleteIfExists(stagingFile);
            } catch (IOException ex) {
                log.warn("Failed to delete staging file for import {}", importJobId, ex);
            }
        }
    }

    private void markFailedSafely(UUID importJobId, String error) {
        try {
            importJobRepository.markFailed(importJobId, error);
        } catch (Exception markEx) {
            log.error("Failed to persist failed status for import {}", importJobId, markEx);
        }
    }
}
