package com.garagebrain.ingestion;

import java.util.UUID;

public class ImportJobNotFoundException extends RuntimeException {

    public ImportJobNotFoundException(UUID vehicleId, UUID importJobId) {
        super("Import job not found: vehicle=" + vehicleId + " job=" + importJobId);
    }
}
