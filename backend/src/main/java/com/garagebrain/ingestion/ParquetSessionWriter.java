package com.garagebrain.ingestion;

import com.garagebrain.config.GarageBrainProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.springframework.stereotype.Component;

@Component
public class ParquetSessionWriter {

    private static final Schema SAMPLE_SCHEMA = new Schema.Parser()
            .parse("""
                    {
                      "type": "record",
                      "name": "PidSampleRecord",
                      "fields": [
                        {"name": "session_id", "type": "string"},
                        {"name": "ts_epoch_ms", "type": "long"},
                        {"name": "pid_name", "type": "string"},
                        {"name": "value", "type": "double"},
                        {"name": "unit", "type": "string"}
                      ]
                    }
                    """);

    private final Path dataRoot;

    public ParquetSessionWriter(GarageBrainProperties properties) {
        this.dataRoot = Path.of(properties.dataDir());
    }

    public Path write(UUID vehicleId, UUID sessionId, List<PidSample> samples) throws IOException {
        Path sessionDir = dataRoot.resolve("vehicles")
                .resolve(vehicleId.toString())
                .resolve("sessions");
        Files.createDirectories(sessionDir);
        Path parquetFile = sessionDir.resolve(sessionId + ".parquet");

        LocalOutputFile outputFile = new LocalOutputFile(parquetFile);
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                .withSchema(SAMPLE_SCHEMA)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {
            for (PidSample sample : samples) {
                GenericRecord record = new GenericData.Record(SAMPLE_SCHEMA);
                record.put("session_id", sessionId.toString());
                record.put("ts_epoch_ms", sample.timestamp().toEpochMilli());
                record.put("pid_name", sample.pidName());
                record.put("value", sample.value());
                record.put("unit", sample.unit());
                writer.write(record);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(parquetFile);
            if (isDiskFull(ex)) {
                throw new IOException("Disk full while writing Parquet archive. Free space under "
                        + dataRoot + " and retry.", ex);
            }
            throw ex;
        }

        return parquetFile;
    }

    private static boolean isDiskFull(IOException ex) {
        String message = ex.getMessage();
        if (message != null && message.toLowerCase().contains("no space left on device")) {
            return true;
        }
        Throwable cause = ex.getCause();
        return cause instanceof IOException causeIo && isDiskFull(causeIo);
    }
}
