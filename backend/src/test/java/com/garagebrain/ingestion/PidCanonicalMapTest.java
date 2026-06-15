package com.garagebrain.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class PidCanonicalMapTest {

    @Test
    void loadsEngineRpmAlias() throws Exception {
        PidCanonicalMap map = new PidCanonicalMap();
        assertEquals("engine_rpm", map.canonicalName("Engine RPM"));
        assertNotNull(map.canonicalName("Time"));
    }
}
