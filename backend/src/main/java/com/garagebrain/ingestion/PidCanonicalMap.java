package com.garagebrain.ingestion;

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
        return aliases.get(header.trim());
    }

    public String unitFor(String canonicalPid) {
        return units.getOrDefault(canonicalPid, "");
    }

    public Map<String, String> aliases() {
        return aliases;
    }
}
