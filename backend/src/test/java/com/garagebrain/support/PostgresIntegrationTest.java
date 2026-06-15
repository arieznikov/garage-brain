package com.garagebrain.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL + temp data directory for integration tests.
 * Starts Testcontainers when Docker is available; otherwise uses docker-compose on localhost:5432.
 */
public abstract class PostgresIntegrationTest {

    private static final Path DATA_DIR = createDataDir();
    private static final PostgreSQLContainer<?> POSTGRES = tryStartContainer();
    private static final boolean USING_CONTAINER = POSTGRES != null;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (USING_CONTAINER) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/garagebrain");
            registry.add("spring.datasource.username", () -> "garagebrain");
            registry.add("spring.datasource.password", () -> "garagebrain");
        }
        registry.add("garagebrain.data-dir", () -> DATA_DIR.toString());
    }

    private static PostgreSQLContainer<?> tryStartContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("garagebrain")
                .withUsername("garagebrain")
                .withPassword("garagebrain");
        try {
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
            return container;
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Path createDataDir() {
        try {
            return Files.createTempDirectory("garage-brain-it");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
