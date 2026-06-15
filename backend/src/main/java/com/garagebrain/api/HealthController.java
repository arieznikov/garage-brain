package com.garagebrain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Service liveness and readiness checks")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns whether the API process is up and responding.")
    public HealthResponse health() {
        return new HealthResponse("ok", "garage-brain-api");
    }

    public record HealthResponse(String status, String service) {
    }
}
