package com.garagebrain.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

final class PidCanonicalMapData {

    private PidCanonicalMapData() {
    }

    static Map<String, String> aliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("Time", "time");
        aliases.put("time", "time");
        aliases.put("Engine RPM", "engine_rpm");
        aliases.put("RPM", "engine_rpm");
        aliases.put("Vehicle speed", "vehicle_speed");
        aliases.put("Speed", "vehicle_speed");
        aliases.put("Engine coolant temperature", "coolant_temp");
        aliases.put("Coolant temp", "coolant_temp");
        aliases.put("STFT B1", "stft_b1");
        aliases.put("LTFT B1", "ltft_b1");
        aliases.put("Battery voltage", "battery_voltage");
        aliases.put("Voltage", "battery_voltage");
        return Map.copyOf(aliases);
    }

    static Map<String, String> units() {
        return Map.of(
                "engine_rpm", "rpm",
                "vehicle_speed", "km/h",
                "coolant_temp", "C",
                "stft_b1", "%",
                "ltft_b1", "%",
                "battery_voltage", "V");
    }
}
