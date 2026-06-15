package com.garagebrain.ingestion;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PidCanonicalMap {

    private final Map<String, String> aliases;
    private final Map<String, String> units;

    public PidCanonicalMap() {
        this(PidCanonicalMapData.aliases(), PidCanonicalMapData.units());
    }

    PidCanonicalMap(Map<String, String> aliases, Map<String, String> units) {
        this.aliases = Map.copyOf(aliases);
        this.units = Map.copyOf(units);
    }

    public String canonicalName(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        String direct = aliases.get(trimmed);
        if (direct != null) {
            return direct;
        }

        String normalized = normalizeHeader(trimmed);
        direct = aliases.get(normalized);
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static String normalizeHeader(String header) {
        String normalized = header.trim();
        normalized = normalized.replaceFirst("^\\[[^]]+]\\s*", "");
        normalized = normalized.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        return normalized;
    }

    public String unitFor(String canonicalPid) {
        return units.getOrDefault(canonicalPid, "");
    }

    public String normalizeUnitSymbol(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            return "";
        }
        return switch (rawUnit.trim()) {
            case "℃", "°C" -> "C";
            case "°F" -> "F";
            default -> rawUnit.trim();
        };
    }

    public Map<String, String> aliases() {
        return aliases;
    }
}
