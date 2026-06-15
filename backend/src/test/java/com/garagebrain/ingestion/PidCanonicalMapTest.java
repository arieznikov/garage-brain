package com.garagebrain.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PidCanonicalMapTest {

    @Test
    void loadsEngineRpmAlias() {
        PidCanonicalMap map = new PidCanonicalMap();
        assertEquals("engine_rpm", map.canonicalName("Engine RPM"));
        assertEquals("time", map.canonicalName("Time"));
    }

    @Test
    void normalizesCarScannerHeadersWithUnits() {
        PidCanonicalMap map = new PidCanonicalMap();
        assertEquals("engine_rpm", map.canonicalName("Engine RPM (rpm)"));
        assertEquals("coolant_temp", map.canonicalName("Engine coolant temperature (℃)"));
        assertEquals("battery_voltage", map.canonicalName("OBD Module Voltage (V)"));
        assertEquals("vehicle_speed", map.canonicalName("Vehicle speed (km/h)"));
    }
}
