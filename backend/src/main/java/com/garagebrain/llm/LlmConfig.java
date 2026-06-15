package com.garagebrain.llm;

import com.garagebrain.config.GarageBrainProperties;
import java.time.Duration;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {

    @Bean
    RestClient llmRestClient(GarageBrainProperties properties) {
        String baseUrl = normalizeBaseUrl(properties.llm());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Bean
    VehicleReportLlmClient vehicleReportLlmClient(RestClient llmRestClient, GarageBrainProperties properties) {
        return isOpenAiCompatible(properties.llm())
                ? new OpenAiCompatibleClient(llmRestClient, properties)
                : new OllamaClient(llmRestClient, properties);
    }

    static boolean isOpenAiCompatible(GarageBrainProperties.Llm llm) {
        String provider = llm.provider() == null ? "ollama" : llm.provider().trim().toLowerCase(Locale.ROOT);
        return provider.equals("openai-compatible") || provider.equals("openai") || provider.equals("lmstudio");
    }

    static String normalizeBaseUrl(GarageBrainProperties.Llm llm) {
        String baseUrl = llm.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return isOpenAiCompatible(llm) ? "http://localhost:1234/v1" : "http://localhost:11434";
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (isOpenAiCompatible(llm) && !trimmed.endsWith("/v1")) {
            return trimmed + "/v1";
        }
        return trimmed;
    }
}
