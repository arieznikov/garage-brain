package com.garagebrain.ingestion;

public class ImportFailedException extends Exception {

    public ImportFailedException(String message) {
        super(message);
    }

    public ImportFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
