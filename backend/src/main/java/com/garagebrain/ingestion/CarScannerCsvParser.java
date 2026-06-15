package com.garagebrain.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

/**
 * Parses Car Scanner CSV exports:
 * <ul>
 *   <li>CSV #2 — comma-separated wide layout, numeric seconds in the time column</li>
 *   <li>Horizontal — comma-separated wide layout, wall-clock {@code HH:mm:ss.SSS} time</li>
 *   <li>Vertical — semicolon-separated long layout ({@code SECONDS, PID, VALUE, ...})</li>
 * </ul>
 */
@Service
public class CarScannerCsvParser {

    static final String SOURCE = "car-scanner-csv2";
    static final String SOURCE_HORIZONTAL = "car-scanner-horizontal";
    static final String SOURCE_VERTICAL = "car-scanner-vertical";
    static final String UNSUPPORTED_HINT =
            "Export from Car Scanner → Data recording → Share (CSV #2, horizontal, or vertical).";

    private static final DateTimeFormatter WALL_CLOCK_TIME = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private final PidCanonicalMap canonicalMap;

    public CarScannerCsvParser(PidCanonicalMap canonicalMap) {
        this.canonicalMap = canonicalMap;
    }

    public ParsedSession parse(InputStream inputStream) throws ParseException, IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                throw new ParseException("Empty CSV. " + UNSUPPORTED_HINT);
            }

            char delimiter = detectDelimiter(firstLine);
            CSVFormat csvFormat = csvFormat(delimiter);

            try (Reader combined = prependLine(firstLine, reader);
                    CSVParser parser = csvFormat.parse(combined)) {

                List<String> rawHeaders = parser.getHeaderNames().stream()
                        .filter(header -> header != null && !header.isBlank())
                        .toList();
                if (rawHeaders.isEmpty()) {
                    throw new ParseException("Empty CSV header. " + UNSUPPORTED_HINT);
                }

                if (isVerticalFormat(rawHeaders)) {
                    return parseVertical(parser, rawHeaders);
                }
                return parseHorizontal(parser, rawHeaders);
            }
        } catch (IllegalArgumentException ex) {
            throw new ParseException("Malformed CSV. " + UNSUPPORTED_HINT, ex);
        }
    }

    private ParsedSession parseVertical(CSVParser parser, List<String> rawHeaders) throws ParseException, IOException {
        String secondsHeader = requireHeader(rawHeaders, "SECONDS");
        String pidHeader = requireHeader(rawHeaders, "PID");
        String valueHeader = requireHeader(rawHeaders, "VALUE");
        String unitsHeader = findHeader(rawHeaders, "UNITS");

        List<PidSample> samples = new ArrayList<>();
        Instant driveStart = null;
        Instant driveEnd = null;

        for (CSVRecord record : parser) {
            double seconds = parseSeconds(record.get(secondsHeader));
            Instant ts = instantFromSeconds(seconds);
            if (driveStart == null) {
                driveStart = ts;
            }
            driveEnd = ts;

            String canonical = canonicalMap.canonicalName(record.get(pidHeader));
            if (canonical == null || "time".equals(canonical)) {
                continue;
            }

            String rawValue = record.get(valueHeader);
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }

            String unit = unitsHeader == null
                    ? canonicalMap.unitFor(canonical)
                    : canonicalMap.normalizeUnitSymbol(record.get(unitsHeader));
            if (unit.isBlank()) {
                unit = canonicalMap.unitFor(canonical);
            }

            samples.add(new PidSample(ts, canonical, parseDouble(rawValue), unit));
        }

        return finishSession(SOURCE_VERTICAL, samples, driveStart, driveEnd);
    }

    private ParsedSession parseHorizontal(CSVParser parser, List<String> rawHeaders) throws ParseException, IOException {
        String timeHeader = resolveTimeHeader(rawHeaders);
        if (timeHeader == null) {
            throw new ParseException("Missing time column. " + UNSUPPORTED_HINT);
        }

        List<ColumnBinding> bindings = resolveBindings(rawHeaders, timeHeader);
        if (bindings.isEmpty()) {
            throw new ParseException("No recognized PID columns. " + UNSUPPORTED_HINT);
        }

        List<PidSample> samples = new ArrayList<>();
        Instant driveStart = null;
        Instant driveEnd = null;
        LocalTime wallClockStart = null;
        boolean wallClockTime = false;
        String source = SOURCE;

        for (CSVRecord record : parser) {
            String rawTime = record.get(timeHeader);
            Instant ts;
            if (wallClockStart == null) {
                if (isWallClockTime(rawTime)) {
                    wallClockTime = true;
                    source = SOURCE_HORIZONTAL;
                    wallClockStart = parseWallClock(rawTime);
                    ts = Instant.EPOCH;
                } else {
                    ts = instantFromSeconds(parseSeconds(rawTime));
                }
                driveStart = ts;
            } else if (wallClockTime) {
                ts = instantFromWallClockOffset(wallClockStart, parseWallClock(rawTime));
            } else {
                ts = instantFromSeconds(parseSeconds(rawTime));
            }
            driveEnd = ts;

            for (ColumnBinding binding : bindings) {
                String raw = record.get(binding.rawHeader());
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                samples.add(new PidSample(ts, binding.canonicalPid(), parseDouble(raw), binding.unit()));
            }
        }

        return finishSession(source, samples, driveStart, driveEnd);
    }

    private ParsedSession finishSession(
            String source, List<PidSample> samples, Instant driveStart, Instant driveEnd) throws ParseException {
        if (samples.isEmpty()) {
            throw new ParseException("No sample rows found. " + UNSUPPORTED_HINT);
        }
        return new ParsedSession(source, driveStart, driveEnd, List.copyOf(samples));
    }

    private static boolean isVerticalFormat(List<String> rawHeaders) {
        return findHeader(rawHeaders, "PID") != null
                && findHeader(rawHeaders, "VALUE") != null
                && findHeader(rawHeaders, "SECONDS") != null;
    }

    private static char detectDelimiter(String firstLine) {
        int semicolons = countUnquoted(firstLine, ';');
        int commas = countUnquoted(firstLine, ',');
        return semicolons > commas ? ';' : ',';
    }

    private static int countUnquoted(String line, char target) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && ch == target) {
                count++;
            }
        }
        return count;
    }

    private static CSVFormat csvFormat(char delimiter) {
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .get();
    }

    private static Reader prependLine(String firstLine, BufferedReader rest) {
        return new Reader() {
            private final StringReader head = new StringReader(firstLine + "\n");

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                int read = head.read(buffer, offset, length);
                if (read > 0) {
                    return read;
                }
                return rest.read(buffer, offset, length);
            }

            @Override
            public void close() throws IOException {
                rest.close();
            }
        };
    }

    private String resolveTimeHeader(List<String> rawHeaders) {
        for (String header : rawHeaders) {
            String canonical = canonicalMap.canonicalName(header);
            if ("time".equals(canonical)) {
                return header;
            }
        }
        for (String header : rawHeaders) {
            if ("time".equalsIgnoreCase(header.trim())) {
                return header;
            }
        }
        return null;
    }

    private List<ColumnBinding> resolveBindings(List<String> rawHeaders, String timeHeader) {
        List<ColumnBinding> bindings = new ArrayList<>();
        for (String header : rawHeaders) {
            if (header.equals(timeHeader)) {
                continue;
            }
            String canonical = canonicalMap.canonicalName(header);
            if (canonical != null && !"time".equals(canonical)) {
                bindings.add(new ColumnBinding(header, canonical, canonicalMap.unitFor(canonical)));
            }
        }
        return bindings;
    }

    private static String requireHeader(List<String> headers, String expected) throws ParseException {
        String header = findHeader(headers, expected);
        if (header == null) {
            throw new ParseException("Missing " + expected + " column. " + UNSUPPORTED_HINT);
        }
        return header;
    }

    private static String findHeader(List<String> headers, String expected) {
        for (String header : headers) {
            if (header.equalsIgnoreCase(expected)) {
                return header;
            }
        }
        return null;
    }

    private static boolean isWallClockTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.contains(":") && !trimmed.matches("-?\\d+(\\.\\d+)?");
    }

    private static LocalTime parseWallClock(String raw) throws ParseException {
        try {
            return LocalTime.parse(raw.trim(), WALL_CLOCK_TIME);
        } catch (Exception ex) {
            throw new ParseException("Invalid wall-clock time value: " + raw, ex);
        }
    }

    private static Instant instantFromWallClockOffset(LocalTime start, LocalTime current) {
        Duration offset = Duration.between(start, current);
        if (offset.isNegative()) {
            offset = offset.plusHours(24);
        }
        return Instant.EPOCH.plus(offset);
    }

    private static Instant instantFromSeconds(double seconds) {
        long wholeSeconds = (long) seconds;
        long nanos = (long) ((seconds - wholeSeconds) * 1_000_000_000L);
        return Instant.ofEpochSecond(wholeSeconds, nanos);
    }

    private static double parseSeconds(String raw) throws ParseException {
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new ParseException("Invalid time value: " + raw, ex);
        }
    }

    private static double parseDouble(String raw) throws ParseException {
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new ParseException("Invalid numeric value: " + raw, ex);
        }
    }

    private record ColumnBinding(String rawHeader, String canonicalPid, String unit) {
    }
}
