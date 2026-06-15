package com.garagebrain.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

/**
 * Parses Car Scanner CSV export variant #2 (comma-separated, header row with PID names).
 *
 * <pre>
 * Time,Engine RPM,Vehicle speed,...
 * 0,850,0,...
 * </pre>
 */
@Service
public class CarScannerCsvParser {

    static final String SOURCE = "car-scanner-csv2";
    static final String UNSUPPORTED_HINT =
            "Export as CSV #2 from Car Scanner → Data recording → Share.";

    private final PidCanonicalMap canonicalMap;

    public CarScannerCsvParser(PidCanonicalMap canonicalMap) {
        this.canonicalMap = canonicalMap;
    }

    public ParsedSession parse(InputStream inputStream) throws ParseException, IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .get()
                        .parse(reader)) {

            List<String> rawHeaders = parser.getHeaderNames();
            if (rawHeaders.isEmpty()) {
                throw new ParseException("Empty CSV header. " + UNSUPPORTED_HINT);
            }

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

            for (CSVRecord record : parser) {
                double seconds = parseSeconds(record.get(timeHeader));
                Instant ts = Instant.ofEpochSecond((long) seconds, (long) ((seconds % 1) * 1_000_000_000));
                if (driveStart == null) {
                    driveStart = ts;
                }
                driveEnd = ts;

                for (ColumnBinding binding : bindings) {
                    String raw = record.get(binding.rawHeader());
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    double value = parseDouble(raw);
                    samples.add(new PidSample(ts, binding.canonicalPid(), value, binding.unit()));
                }
            }

            if (samples.isEmpty()) {
                throw new ParseException("No sample rows found. " + UNSUPPORTED_HINT);
            }

            return new ParsedSession(SOURCE, driveStart, driveEnd, List.copyOf(samples));
        } catch (IllegalArgumentException ex) {
            throw new ParseException("Malformed CSV. " + UNSUPPORTED_HINT, ex);
        }
    }

    private String resolveTimeHeader(List<String> rawHeaders) {
        for (String header : rawHeaders) {
            String canonical = canonicalMap.canonicalName(header);
            if ("time".equals(canonical)) {
                return header;
            }
        }
        for (String header : rawHeaders) {
            if (header.equalsIgnoreCase("time") || header.equalsIgnoreCase("Time")) {
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
