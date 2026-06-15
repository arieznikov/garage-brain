package com.garagebrain.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VehicleReportParserTest {

    private final VehicleReportParser parser = new VehicleReportParser(new ObjectMapper());

    @Test
    void acceptsValidReport() {
        String json =
                """
                {
                  "summary": "Battery voltage dipped at crank.",
                  "urgency": "high",
                  "likely_causes": ["weak battery"],
                  "suggested_checks": ["load test battery"],
                  "drive_advice": "Avoid long trips until tested."
                }
                """;

        VehicleReport report = parser.parse(json);

        assertThat(report.urgency()).isEqualTo("high");
        assertThat(report.likelyCauses()).containsExactly("weak battery");
    }

    @Test
    void rejectsInvalidUrgency() {
        String json =
                """
                {
                  "summary": "x",
                  "urgency": "critical",
                  "likely_causes": ["a"],
                  "suggested_checks": ["b"],
                  "drive_advice": "c"
                }
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(InvalidVehicleReportException.class);
    }

    @Test
    void rejectsEmptyLikelyCauses() {
        String json =
                """
                {
                  "summary": "x",
                  "urgency": "low",
                  "likely_causes": [],
                  "suggested_checks": ["b"],
                  "drive_advice": "c"
                }
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(InvalidVehicleReportException.class);
    }
}
