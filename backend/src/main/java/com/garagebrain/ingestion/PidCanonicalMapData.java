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
        aliases.put("SECONDS", "time");
        aliases.put("Seconds", "time");

        aliases.put("Engine RPM", "engine_rpm");
        aliases.put("Engine RPM x1000", "engine_rpm_x1000");
        aliases.put("RPM", "engine_rpm");

        aliases.put("Vehicle speed", "vehicle_speed");
        aliases.put("Speed", "vehicle_speed");
        aliases.put("Average speed", "average_speed");
        aliases.put("Speed (GPS)", "gps_speed");
        aliases.put("Average speed (GPS)", "gps_average_speed");

        aliases.put("Engine coolant temperature", "coolant_temp");
        aliases.put("Coolant temp", "coolant_temp");
        aliases.put("Intake air temperature", "intake_air_temp");

        aliases.put("STFT B1", "stft_b1");
        aliases.put("LTFT B1", "ltft_b1");

        aliases.put("Battery voltage", "battery_voltage");
        aliases.put("Voltage", "battery_voltage");
        aliases.put("OBD Module Voltage", "battery_voltage");

        aliases.put("MAF air flow rate", "maf");
        aliases.put("Calculated engine load value", "engine_load");
        aliases.put("Calculated boost", "boost");
        aliases.put("Vehicle acceleration", "vehicle_acceleration");
        aliases.put("Power from MAF", "power_from_maf");

        aliases.put("Distance travelled", "distance_trip");
        aliases.put("Distance travelled (Today)", "distance_today");
        aliases.put("Distance travelled (total)", "distance_total");
        aliases.put("Distance travelled (Week)", "distance_week");
        aliases.put("Altitude (GPS)", "altitude_gps");

        aliases.put("Latitude", "latitude");
        aliases.put("Longtitude", "longitude");
        aliases.put("Longitude", "longitude");
        return Map.copyOf(aliases);
    }

    static Map<String, String> units() {
        return Map.ofEntries(
                Map.entry("engine_rpm", "rpm"),
                Map.entry("engine_rpm_x1000", "rpm"),
                Map.entry("vehicle_speed", "km/h"),
                Map.entry("average_speed", "km/h"),
                Map.entry("gps_speed", "km/h"),
                Map.entry("gps_average_speed", "km/h"),
                Map.entry("coolant_temp", "C"),
                Map.entry("intake_air_temp", "C"),
                Map.entry("stft_b1", "%"),
                Map.entry("ltft_b1", "%"),
                Map.entry("battery_voltage", "V"),
                Map.entry("maf", "g/sec"),
                Map.entry("engine_load", "%"),
                Map.entry("boost", "bar"),
                Map.entry("vehicle_acceleration", "g"),
                Map.entry("power_from_maf", "hp"),
                Map.entry("distance_trip", "km"),
                Map.entry("distance_today", "km"),
                Map.entry("distance_total", "km"),
                Map.entry("distance_week", "km"),
                Map.entry("altitude_gps", "m"),
                Map.entry("latitude", "deg"),
                Map.entry("longitude", "deg"));
    }
}
