package com.garagebrain.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarScannerCsvParserTest {

    private CarScannerCsvParser parser;

    @BeforeEach
    void setUp() throws IOException {
        parser = new CarScannerCsvParser(new PidCanonicalMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fixtures/car-scanner-csv2-synthetic-warmup.csv",
            "fixtures/car-scanner-csv2-synthetic-cruise.csv"
    })
    void parsesSyntheticCarScannerFixtures(String resourcePath) throws Exception {
        try (InputStream in = resource(resourcePath)) {
            ParsedSession session = parser.parse(in);

            assertEquals(CarScannerCsvParser.SOURCE, session.source());
            assertTrue(session.samples().size() > 0);
            assertTrue(session.driveStartedAt().isBefore(session.driveEndedAt())
                    || session.driveStartedAt().equals(session.driveEndedAt()));

            var pids = session.samples().stream()
                    .map(PidSample::pidName)
                    .collect(Collectors.toSet());
            assertTrue(pids.contains("engine_rpm"));
            assertTrue(pids.contains("coolant_temp"));
        }
    }

    @Test
    void rejectsUnsupportedFormat() {
        try (InputStream in = resource("fixtures/car-scanner-csv2-unsupported.csv")) {
            ParseException ex = assertThrows(ParseException.class, () -> parser.parse(in));
            assertTrue(ex.getMessage().contains(CarScannerCsvParser.UNSUPPORTED_HINT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void normalizesPidNamesViaCanonicalMap() throws Exception {
        try (InputStream in = resource("fixtures/car-scanner-csv2-synthetic-warmup.csv")) {
            ParsedSession session = parser.parse(in);
            Map<String, Long> counts = session.samples().stream()
                    .collect(Collectors.groupingBy(PidSample::pidName, Collectors.counting()));
            assertTrue(counts.containsKey("stft_b1"));
            assertTrue(counts.containsKey("battery_voltage"));
        }
    }

    private static InputStream resource(String path) {
        InputStream in = CarScannerCsvParserTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing test resource: " + path);
        }
        return in;
    }
}
