package com.garagebrain.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.garagebrain.config.GarageBrainProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParquetSessionWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesParquetWithoutHadoopFileSystem() throws Exception {
        ParquetSessionWriter writer = new ParquetSessionWriter(
                new GarageBrainProperties(tempDir.toString(), new GarageBrainProperties.Llm("ollama", "", "", "")));

        UUID vehicleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        List<PidSample> samples = List.of(
                new PidSample(Instant.parse("2026-06-15T10:00:00Z"), "Engine RPM", 850.0, "rpm"),
                new PidSample(Instant.parse("2026-06-15T10:00:01Z"), "LTFT B1", 2.1, "%"));

        Path parquetPath = writer.write(vehicleId, sessionId, samples);

        assertThat(parquetPath).exists();
        assertThat(Files.size(parquetPath)).isPositive();
    }
}
