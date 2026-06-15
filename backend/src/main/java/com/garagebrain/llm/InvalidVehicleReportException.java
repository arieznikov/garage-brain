package com.garagebrain.llm;

public class InvalidVehicleReportException extends RuntimeException {

    public InvalidVehicleReportException(String message) {
        super(message);
    }

    public InvalidVehicleReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
