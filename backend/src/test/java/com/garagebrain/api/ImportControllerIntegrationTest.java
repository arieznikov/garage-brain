package com.garagebrain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garagebrain.persistence.ImportJobRepository;
import com.garagebrain.persistence.VehicleRepository;
import com.garagebrain.support.PostgresIntegrationTest;
import com.garagebrain.support.TestFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ImportControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    void acceptsUploadImmediatelyAndCompletesOnPoll() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Async API Test");

        byte[] csv = TestFixtures.readClasspath("fixtures/car-scanner-csv2-synthetic-warmup.csv");
        MockMultipartFile file =
                new MockMultipartFile("file", "warmup.csv", MediaType.TEXT_PLAIN_VALUE, csv);

        Instant submitStarted = Instant.now();
        MvcResult accepted = mockMvc.perform(multipart("/api/v1/vehicles/{vehicleId}/imports", vehicleId)
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.importJobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value(ImportJobRepository.STATUS_RUNNING))
                .andExpect(jsonPath("$.statusUrl").isNotEmpty())
                .andReturn();
        assertThat(Duration.between(submitStarted, Instant.now()))
                .isLessThan(Duration.ofSeconds(2));

        String body = accepted.getResponse().getContentAsString();
        String importJobId = body.replaceAll("(?s).*\"importJobId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        String finalStatus = ImportJobRepository.STATUS_RUNNING;
        while (Instant.now().isBefore(deadline)
                && ImportJobRepository.STATUS_RUNNING.equals(finalStatus)) {
            Thread.sleep(200);
            MvcResult poll = mockMvc.perform(get(
                            "/api/v1/vehicles/{vehicleId}/imports/{importJobId}", vehicleId, importJobId))
                    .andExpect(status().isOk())
                    .andReturn();
            finalStatus = poll.getResponse().getContentAsString().replaceAll(
                    "(?s).*\"status\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        }

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/imports/{importJobId}", vehicleId, importJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ImportJobRepository.STATUS_COMPLETED))
                .andExpect(jsonPath("$.result.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.result.sampleCount").isNumber());
    }

    @Test
    void largeFixtureReturnsAcceptedWithoutBlocking() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Large Export Test");

        byte[] csv = TestFixtures.readClasspath("fixtures/exported_records_horizontal/2026-06-15 12-25-55.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.csv", MediaType.TEXT_PLAIN_VALUE, csv);

        Instant submitStarted = Instant.now();
        mockMvc.perform(multipart("/api/v1/vehicles/{vehicleId}/imports", vehicleId).file(file))
                .andExpect(status().isAccepted());
        assertThat(Duration.between(submitStarted, Instant.now()))
                .isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void duplicateUploadReturnsConflict() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Duplicate API Test");

        byte[] csv = TestFixtures.readClasspath("fixtures/car-scanner-csv2-synthetic-warmup.csv");
        MockMultipartFile file =
                new MockMultipartFile("file", "warmup.csv", MediaType.TEXT_PLAIN_VALUE, csv);

        MvcResult accepted = mockMvc.perform(multipart("/api/v1/vehicles/{vehicleId}/imports", vehicleId)
                        .file(file))
                .andExpect(status().isAccepted())
                .andReturn();
        String importJobId = extractImportJobId(accepted.getResponse().getContentAsString());
        waitUntilImportSettles(mockMvc, vehicleId, importJobId);

        mockMvc.perform(multipart("/api/v1/vehicles/{vehicleId}/imports", vehicleId).file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate import"));
    }

    @Test
    void pollUnknownJobReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Poll 404 Test");
        UUID importJobId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/imports/{importJobId}", vehicleId, importJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Import job not found"));
    }

    @Test
    void emptyUploadReturnsUnprocessableEntity() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Empty Upload Test");
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/vehicles/{vehicleId}/imports", vehicleId).file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Import failed"));
    }

    private static String extractImportJobId(String body) {
        return body.replaceAll("(?s).*\"importJobId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static void waitUntilImportSettles(MockMvc mockMvc, UUID vehicleId, String importJobId)
            throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            MvcResult poll = mockMvc.perform(get(
                            "/api/v1/vehicles/{vehicleId}/imports/{importJobId}", vehicleId, importJobId))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = poll.getResponse().getContentAsString().replaceAll(
                    "(?s).*\"status\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            if (!ImportJobRepository.STATUS_RUNNING.equals(status)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Import did not finish before deadline");
    }
}
