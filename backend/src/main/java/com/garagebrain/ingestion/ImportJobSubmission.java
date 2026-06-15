package com.garagebrain.ingestion;

import java.util.UUID;

public record ImportJobSubmission(UUID importJobId, UUID vehicleId, String status) {
}
