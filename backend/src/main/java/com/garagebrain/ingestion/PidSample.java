package com.garagebrain.ingestion;

import java.time.Instant;

public record PidSample(Instant timestamp, String pidName, double value, String unit) {
}
