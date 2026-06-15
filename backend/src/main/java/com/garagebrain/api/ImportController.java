package com.garagebrain.api;

import com.garagebrain.ingestion.DuplicateImportException;
import com.garagebrain.ingestion.ImportFailedException;
import com.garagebrain.ingestion.ImportJobStatus;
import com.garagebrain.ingestion.ImportJobSubmission;
import com.garagebrain.ingestion.ImportResult;
import com.garagebrain.ingestion.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/imports")
@Tag(name = "Imports", description = "Import Car Scanner CSV exports for a vehicle")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Submit CSV import",
            description =
                    "Accepts a Car Scanner CSV and processes it asynchronously. Poll the returned status URL until completed or failed.")
    public ImportAcceptedResponse submitImport(
            @PathVariable UUID vehicleId, @RequestPart("file") MultipartFile file)
            throws DuplicateImportException, ImportFailedException, IOException {
        if (file.isEmpty()) {
            throw new ImportFailedException("Uploaded file is empty");
        }

        ImportJobSubmission submission = importService.submitImport(vehicleId, file.getBytes());
        return ImportAcceptedResponse.from(vehicleId, submission);
    }

    @GetMapping("/{importJobId}")
    @Operation(summary = "Poll import status", description = "Returns running stage, failure details, or the completed session result.")
    public ImportStatusResponse getImportStatus(
            @PathVariable UUID vehicleId, @PathVariable UUID importJobId) {
        return ImportStatusResponse.from(importService.getImportStatus(vehicleId, importJobId));
    }

    public record ImportAcceptedResponse(
            UUID importJobId, UUID vehicleId, String status, String statusUrl) {

        static ImportAcceptedResponse from(UUID vehicleId, ImportJobSubmission submission) {
            return new ImportAcceptedResponse(
                    submission.importJobId(),
                    submission.vehicleId(),
                    submission.status(),
                    "/api/v1/vehicles/" + vehicleId + "/imports/" + submission.importJobId());
        }
    }

    public record ImportStatusResponse(
            UUID importJobId,
            UUID vehicleId,
            String status,
            String stage,
            String error,
            Instant createdAt,
            Instant completedAt,
            ImportResultResponse result) {

        static ImportStatusResponse from(ImportJobStatus status) {
            return new ImportStatusResponse(
                    status.importJobId(),
                    status.vehicleId(),
                    status.status(),
                    status.stage(),
                    status.error(),
                    status.createdAt(),
                    status.completedAt(),
                    status.result() != null ? ImportResultResponse.from(status.result()) : null);
        }
    }

    public record ImportResultResponse(
            UUID sessionId,
            String source,
            int sampleCount,
            Instant driveStartedAt,
            Instant driveEndedAt,
            String parquetPath) {

        static ImportResultResponse from(ImportResult result) {
            return new ImportResultResponse(
                    result.sessionId(),
                    result.source(),
                    result.sampleCount(),
                    result.driveStartedAt(),
                    result.driveEndedAt(),
                    result.parquetPath());
        }
    }
}
