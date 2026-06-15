package com.garagebrain.analytics;

import java.util.List;

public final class TrendMetrics {

    public static final String COOLANT_WARMUP_SLOPE = "coolant_warmup_slope";
    public static final String STFT_DRIFT = "stft_drift";
    public static final String LTFT_DRIFT = "ltft_drift";
    public static final String BATTERY_AT_CRANK = "battery_at_crank";
    public static final String IDLE_RPM_VARIANCE = "idle_rpm_variance";

    public static final String PID_COOLANT_TEMP = "coolant_temp";
    public static final String PID_STFT_B1 = "stft_b1";
    public static final String PID_LTFT_B1 = "ltft_b1";
    public static final String PID_BATTERY_VOLTAGE = "battery_voltage";
    public static final String PID_ENGINE_RPM = "engine_rpm";

    public static final int MIN_SESSIONS_FOR_ALERTS = 5;
    public static final int ROLLING_SESSION_WINDOW = 10;
    public static final double Z_THRESHOLD_DRIFT = 2.5;
    public static final double Z_THRESHOLD_LOWER_IS_WORSE = 2.0;

    public static final String SEVERITY_WARNING = "warning";

    public static final List<String> ALL = List.of(
            COOLANT_WARMUP_SLOPE,
            STFT_DRIFT,
            LTFT_DRIFT,
            BATTERY_AT_CRANK,
            IDLE_RPM_VARIANCE);

    public static final String SEGMENT_METRIC = "metric";

    private TrendMetrics() {
    }
}
