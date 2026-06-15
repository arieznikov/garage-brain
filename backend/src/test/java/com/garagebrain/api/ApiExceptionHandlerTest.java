package com.garagebrain.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garagebrain.ingestion.DuplicateImportException;
import com.garagebrain.ingestion.ImportFailedException;
import com.garagebrain.ingestion.ImportJobNotFoundException;
import com.garagebrain.ingestion.VehicleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void duplicateImport_returnsConflict() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate import"));
    }

    @Test
    void importFailed_returnsUnprocessableEntity() throws Exception {
        mockMvc.perform(get("/test/import-failed"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Import failed"));
    }

    @Test
    void vehicleNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(get("/test/vehicle-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Vehicle not found"));
    }

    @Test
    void importJobNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(get("/test/import-job-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Import job not found"));
    }

    @Test
    void uploadTooLarge_returnsPayloadTooLarge() throws Exception {
        mockMvc.perform(get("/test/upload-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("File too large"));
    }

    @Test
    void illegalArgument_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void validationFailed_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @RestController
    static class StubController {

        @GetMapping("/test/duplicate")
        void duplicate() throws DuplicateImportException {
            throw new DuplicateImportException("already imported");
        }

        @GetMapping("/test/import-failed")
        void importFailed() throws ImportFailedException {
            throw new ImportFailedException("parser error");
        }

        @GetMapping("/test/vehicle-not-found")
        void vehicleNotFound() {
            throw new VehicleNotFoundException(java.util.UUID.randomUUID());
        }

        @GetMapping("/test/import-job-not-found")
        void importJobNotFound() {
            throw new ImportJobNotFoundException(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        }

        @GetMapping("/test/upload-too-large")
        void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(1);
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("nickname required");
        }

        @PostMapping("/test/validate")
        void validate(@jakarta.validation.Valid StubRequest request) {
            // validated by MVC
        }
    }

    record StubRequest(@jakarta.validation.constraints.NotBlank String nickname) {
    }
}
