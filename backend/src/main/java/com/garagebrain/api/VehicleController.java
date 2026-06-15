package com.garagebrain.api;

import com.garagebrain.persistence.VehicleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "Register vehicles for OBD session imports")
public class VehicleController {

    private final VehicleRepository vehicleRepository;

    public VehicleController(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create vehicle", description = "Registers a vehicle that CSV imports are attached to.")
    public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest request) {
        UUID id = UUID.randomUUID();
        vehicleRepository.insert(id, request.nickname());
        return new VehicleResponse(id, request.nickname());
    }

    public record CreateVehicleRequest(@NotBlank String nickname) {
    }

    public record VehicleResponse(UUID id, String nickname) {
    }
}
