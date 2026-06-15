package com.garagebrain.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.config.GarageBrainProperties;
import org.junit.jupiter.api.Test;

class LlmConfigTest {

    @Test
    void appendsV1ForOpenAiCompatibleBaseUrl() {
        GarageBrainProperties.Llm llm =
                new GarageBrainProperties.Llm("lmstudio", "http://localhost:1234", "google/gemma-4-e2b", "");

        assertThat(LlmConfig.normalizeBaseUrl(llm)).isEqualTo("http://localhost:1234/v1");
    }

    @Test
    void leavesOllamaBaseUrlUntouched() {
        GarageBrainProperties.Llm llm = new GarageBrainProperties.Llm("ollama", "http://localhost:11434", "llama3.2", "");

        assertThat(LlmConfig.normalizeBaseUrl(llm)).isEqualTo("http://localhost:11434");
    }
}
