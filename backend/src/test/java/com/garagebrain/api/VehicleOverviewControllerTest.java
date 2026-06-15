package com.garagebrain.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garagebrain.analytics.TrendMetrics;
import com.garagebrain.support.PostgresIntegrationTest;
import com.garagebrain.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class VehicleOverviewControllerTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private com.garagebrain.persistence.VehicleRepository vehicleRepository;

    @Test
    void returnsBaselineProgressForVehicle() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = TestFixtures.insertVehicle(vehicleRepository, "UI Test Jeep");

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/overview", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.nickname").value("UI Test Jeep"))
                .andExpect(jsonPath("$.baselineProgress.required").value(TrendMetrics.MIN_SESSIONS_FOR_ALERTS))
                .andExpect(jsonPath("$.baselineProgress.ready").value(false));
    }

    @Test
    void unknownVehicleReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID vehicleId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/overview", vehicleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Vehicle not found"));
    }
}
