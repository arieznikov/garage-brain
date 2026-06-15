package com.garagebrain.llm;

public record VehicleReportResult(boolean degraded, String message, VehicleReport report, VehicleReportInput input) {

    public static VehicleReportResult ready(VehicleReport report, VehicleReportInput input) {
        return new VehicleReportResult(false, null, report, input);
    }

    public static VehicleReportResult degraded(String message, VehicleReportInput input) {
        return new VehicleReportResult(true, message, null, input);
    }
}
