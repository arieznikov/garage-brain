package com.garagebrain.ingestion;

public class DuplicateImportException extends Exception {

    public DuplicateImportException(String message) {
        super(message);
    }
}
