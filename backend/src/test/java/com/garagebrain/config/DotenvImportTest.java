package com.garagebrain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.support.PostgresIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DotenvImportTest extends PostgresIntegrationTest {

  @Autowired
  private GarageBrainProperties properties;

  @Test
  void loadsLlmSettingsFromRepoRootDotenv() throws Exception {
    Path env = Path.of("../.env").toAbsolutePath().normalize();
    Assumptions.assumeTrue(Files.isRegularFile(env), "No repo-root .env — skipped");

    String envText = Files.readString(env);
    Assumptions.assumeTrue(envText.contains("LLM_PROVIDER="), ".env has no LLM_PROVIDER — skipped");

    assertThat(properties.llm().provider()).isNotBlank();
    if (envText.contains("LLM_PROVIDER=openai-compatible")) {
      assertThat(properties.llm().provider()).isEqualTo("openai-compatible");
    }
    if (envText.contains("LLM_MODEL=google/gemma-4-e2b")) {
      assertThat(properties.llm().model()).isEqualTo("google/gemma-4-e2b");
    }
  }
}
