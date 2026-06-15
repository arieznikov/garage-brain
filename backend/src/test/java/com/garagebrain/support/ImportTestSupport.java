package com.garagebrain.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garagebrain.persistence.ImportJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public final class ImportTestSupport {

    private ImportTestSupport() {
    }

    public static String uploadCsv(MockMvc mockMvc, UUID vehicleId, byte[] csv, String filename)
            throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", filename, MediaType.TEXT_PLAIN_VALUE, csv);
        MvcResult accepted = mockMvc.perform(multipart("/api/v1/vehicles/{vehicleId}/imports", vehicleId)
                        .file(file))
                .andExpect(status().isAccepted())
                .andReturn();
        String importJobId = extractImportJobId(accepted.getResponse().getContentAsString());
        waitUntilImportSettles(mockMvc, vehicleId, importJobId);
        return importJobId;
    }

    public static void waitUntilImportSettles(MockMvc mockMvc, UUID vehicleId, String importJobId)
            throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            MvcResult poll = mockMvc.perform(get(
                            "/api/v1/vehicles/{vehicleId}/imports/{importJobId}", vehicleId, importJobId))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = extractJsonString(poll.getResponse().getContentAsString(), "status");
            if (ImportJobRepository.STATUS_FAILED.equals(status)) {
                String error = extractJsonString(poll.getResponse().getContentAsString(), "error");
                throw new IllegalStateException("Import failed: " + error);
            }
            if (!ImportJobRepository.STATUS_RUNNING.equals(status)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Import did not finish before deadline");
    }

    public static String extractImportJobId(String body) {
        return extractJsonString(body, "importJobId");
    }

    private static String extractJsonString(String body, String field) {
        return body.replaceAll("(?s).*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
