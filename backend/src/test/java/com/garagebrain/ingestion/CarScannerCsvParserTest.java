package com.garagebrain.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CarScannerCsvParserTest {

    private CarScannerCsvParser parser;

    @BeforeEach
    void setUp() {
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

    @ParameterizedTest
    @MethodSource("realExportFixtures")
    void parsesRealCarScannerExports(String resourcePath, String expectedSource) throws Exception {
        try (InputStream in = resource(resourcePath)) {
            ParsedSession session = parser.parse(in);

            assertEquals(expectedSource, session.source());
            assertTrue(session.samples().size() > 0, () -> "no samples in " + resourcePath);
            assertTrue(session.driveStartedAt().compareTo(session.driveEndedAt()) <= 0);

            var pids = session.samples().stream()
                    .map(PidSample::pidName)
                    .collect(Collectors.toSet());
            assertTrue(!pids.isEmpty(), () -> "no mapped PIDs in " + resourcePath);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fixtures/exported_records_horizontal/2026-06-15 12-25-55.csv",
            "fixtures/exported_records_horizontal_backfill/2026-06-15 12-25-55.csv",
            "fixtures/exported_records_vertical/2026-06-15 12-25-55.csv"
    })
    void parsesObdRichCarScannerExports(String resourcePath) throws Exception {
        try (InputStream in = resource(resourcePath)) {
            ParsedSession session = parser.parse(in);
            var pids = session.samples().stream()
                    .map(PidSample::pidName)
                    .collect(Collectors.toSet());
            assertTrue(pids.contains("engine_rpm"), () -> "missing engine_rpm in " + resourcePath);
            assertTrue(pids.contains("coolant_temp"), () -> "missing coolant_temp in " + resourcePath);
            assertTrue(pids.contains("battery_voltage"), () -> "missing battery_voltage in " + resourcePath);
        }
    }

    @Test
    void horizontalAndBackfillExportsShareSessionShape() throws Exception {
        String sessionName = "2026-06-15 12-25-55.csv";
        ParsedSession horizontal = parseFixture("fixtures/exported_records_horizontal/" + sessionName);
        ParsedSession backfill = parseFixture("fixtures/exported_records_horizontal_backfill/" + sessionName);

        assertEquals(CarScannerCsvParser.SOURCE_HORIZONTAL, horizontal.source());
        assertEquals(CarScannerCsvParser.SOURCE_HORIZONTAL, backfill.source());
        assertTrue(backfill.samples().size() >= horizontal.samples().size());

        var horizontalPids = horizontal.samples().stream()
                .map(PidSample::pidName)
                .collect(Collectors.toSet());
        var backfillPids = backfill.samples().stream()
                .map(PidSample::pidName)
                .collect(Collectors.toSet());
        assertEquals(horizontalPids, backfillPids);
    }

    @Test
    void rejectsUnsupportedFormat() throws Exception {
        try (InputStream in = resource("fixtures/car-scanner-csv2-unsupported.csv")) {
            ParseException ex = assertThrows(ParseException.class, () -> parser.parse(in));
            assertTrue(ex.getMessage().contains(CarScannerCsvParser.UNSUPPORTED_HINT));
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

    private ParsedSession parseFixture(String resourcePath) throws Exception {
        try (InputStream in = resource(resourcePath)) {
            return parser.parse(in);
        }
    }

    private static Stream<Arguments> realExportFixtures() throws IOException {
        Stream<Arguments> horizontal = fixturePaths("exported_records_horizontal")
                .map(path -> Arguments.of(path, CarScannerCsvParser.SOURCE_HORIZONTAL));
        Stream<Arguments> backfill = fixturePaths("exported_records_horizontal_backfill")
                .map(path -> Arguments.of(path, CarScannerCsvParser.SOURCE_HORIZONTAL));
        Stream<Arguments> vertical = fixturePaths("exported_records_vertical")
                .map(path -> Arguments.of(path, CarScannerCsvParser.SOURCE_VERTICAL));
        return Stream.concat(Stream.concat(horizontal, backfill), vertical);
    }

    private static Stream<String> fixturePaths(String subdir) throws IOException {
        Path dir = Path.of("src/test/resources/fixtures", subdir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Missing fixture directory: " + dir);
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(path -> path.toString().endsWith(".csv"))
                    .sorted()
                    .map(path -> "fixtures/" + subdir + "/" + path.getFileName())
                    .toList()
                    .stream();
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
