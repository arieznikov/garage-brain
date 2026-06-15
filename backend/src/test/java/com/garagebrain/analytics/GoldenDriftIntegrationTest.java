package com.garagebrain.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garagebrain.support.GoldenDriftFixtures;
import com.garagebrain.support.ImportTestSupport;
import com.garagebrain.support.PostgresIntegrationTest;
import com.garagebrain.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * DESIGN.md success criterion: golden 6-session LTFT drift → alert after baseline gate (≥5 drives).
 * Exercises parser → Parquet → Postgres → baselines → alert evaluator through the public REST API.
 */
@SpringBootTest
class GoldenDriftIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private com.garagebrain.persistence.VehicleRepository vehicleRepository;

    private MockMvc mockMvc;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        vehicleId = TestFixtures.insertVehicle(vehicleRepository, "Golden Drift");
    }

    @Test
    void baselineGateBlocksAlertsUntilFiveSessions() throws Exception {
        for (int i = 0; i < 4; i++) {
            ImportTestSupport.uploadCsv(
                    mockMvc,
                    vehicleId,
                    GoldenDriftFixtures.cruiseCsv(GoldenDriftFixtures.BASELINE_LTFT[i], i),
                    "baseline-" + i + ".csv");
        }

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/overview", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineProgress.sessions").value(4))
                .andExpect(jsonPath("$.baselineProgress.ready").value(false))
                .andExpect(jsonPath("$.alerts").isEmpty());
    }

    @Test
    void sixthSessionLtftDriftSurfacesAlertViaRestApi() throws Exception {
        for (int i = 0; i < GoldenDriftFixtures.BASELINE_LTFT.length; i++) {
            ImportTestSupport.uploadCsv(
                    mockMvc,
                    vehicleId,
                    GoldenDriftFixtures.cruiseCsv(GoldenDriftFixtures.BASELINE_LTFT[i], i),
                    "baseline-" + i + ".csv");
        }

        ImportTestSupport.uploadCsv(
                mockMvc,
                vehicleId,
                TestFixtures.readClasspath(GoldenDriftFixtures.DRIFT_FIXTURE),
                "drift.csv");

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/overview", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineProgress.sessions").value(6))
                .andExpect(jsonPath("$.baselineProgress.ready").value(true))
                .andExpect(jsonPath("$.alerts[?(@.metric == 'ltft_drift')]").isNotEmpty());
    }
}
