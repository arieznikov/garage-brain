package com.garagebrain.support;

import com.garagebrain.persistence.VehicleRepository;
import java.io.IOException;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static byte[] readClasspath(String resourcePath) throws IOException {
        try (var stream = TestFixtures.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture: " + resourcePath);
            }
            return stream.readAllBytes();
        }
    }

    public static UUID insertVehicle(VehicleRepository vehicleRepository, String nickname) {
        UUID vehicleId = UUID.randomUUID();
        vehicleRepository.insert(vehicleId, nickname);
        return vehicleId;
    }
}
