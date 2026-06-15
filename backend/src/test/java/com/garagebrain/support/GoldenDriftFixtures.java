package com.garagebrain.support;

import java.nio.charset.StandardCharsets;

/**
 * Synthetic six-session scenario from DESIGN.md success criteria:
 * five stable cruise logs establish LTFT baseline, sixth injects drift → alert after gate.
 */
public final class GoldenDriftFixtures {

    public static final double[] BASELINE_LTFT = {1.98, 2.0, 2.02, 1.99, 2.01};
    public static final String DRIFT_FIXTURE = "fixtures/car-scanner-csv2-synthetic-cruise-drift.csv";

    private GoldenDriftFixtures() {
    }

    public static byte[] cruiseCsv(double ltft, int sessionIndex) {
        String csv =
                """
                        Time,Engine RPM,Vehicle speed,Engine coolant temperature,STFT B1,LTFT B1,Battery voltage
                        0,2200,60,88,1.2,%s,14.0
                        1,2300,62,88,1.3,%s,14.0
                        2,2250,61,89,1.4,%s,14.0
                        3,2280,63,89,1.5,%s,14.0
                        4,2260,62,90,1.6,%s,14.0
                        %d,2200,60,88,1.2,%s,14.0
                        """
                        .formatted(ltft, ltft, ltft, ltft, ltft, 100 + sessionIndex, ltft);
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}
