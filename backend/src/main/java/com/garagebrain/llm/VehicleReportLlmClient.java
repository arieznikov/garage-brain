package com.garagebrain.llm;

/** BYOM adapter — Ollama native API or OpenAI-compatible (LM Studio, etc.). */
public interface VehicleReportLlmClient {

    boolean isConfigured();

    String configuredEndpoint();

    VehicleReport generateReport(VehicleReportInput input);
}
