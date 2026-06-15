package com.garagebrain.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garagebrain.llm.VehicleReport;
import com.garagebrain.llm.VehicleReportInput;
import com.garagebrain.llm.VehicleReportResult;
import com.garagebrain.llm.VehicleReportService;
import com.garagebrain.support.PostgresIntegrationTest;
import com.garagebrain.support.TestFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class VehicleReportControllerTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private com.garagebrain.persistence.VehicleRepository vehicleRepository;

    @MockitoBean
    private VehicleReportService vehicleReportService;

    @Test
    void returnsReadyReport() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Report Test");
        VehicleReportInput input = new VehicleReportInput(
                new VehicleReportInput.Vehicle("Report Test"),
                new VehicleReportInput.BaselineProgress(2, 5, false),
                List.of());
        VehicleReport report = new VehicleReport(
                "Trends look stable.",
                "low",
                List.of("normal variance"),
                List.of("keep monitoring"),
                "Drive as usual.");
        when(vehicleReportService.generateReport(eq(vehicleId)))
                .thenReturn(VehicleReportResult.ready(report, input));

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/report", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.report.summary").value("Trends look stable."))
                .andExpect(jsonPath("$.report.urgency").value("low"));
    }

    @Test
    void returnsDegradedReport() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Degraded Test");
        VehicleReportInput input = new VehicleReportInput(
                new VehicleReportInput.Vehicle("Degraded Test"),
                new VehicleReportInput.BaselineProgress(1, 5, false),
                List.of());
        when(vehicleReportService.generateReport(eq(vehicleId)))
                .thenReturn(VehicleReportResult.degraded("Ollama is not reachable", input));

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/report", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.message").value("Ollama is not reachable"))
                .andExpect(jsonPath("$.report").isEmpty());
    }

    @Test
    void unknownVehicleReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = UUID.randomUUID();

        when(vehicleReportService.generateReport(eq(vehicleId)))
                .thenThrow(new com.garagebrain.ingestion.VehicleNotFoundException(vehicleId));

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/report", vehicleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Vehicle not found"));
    }
}
