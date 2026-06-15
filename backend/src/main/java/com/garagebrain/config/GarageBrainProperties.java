package com.garagebrain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "garagebrain")
public record GarageBrainProperties(String dataDir, Llm llm) {

    public record Llm(String provider, String baseUrl, String model, String apiKey) {
    }
}
