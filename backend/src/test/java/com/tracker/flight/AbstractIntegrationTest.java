package com.tracker.flight;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;

@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
    // Optional existing disposable PostgreSQL for environments without Docker.
    // Default remains Testcontainers, including CI.
    private static final String EXTERNAL_URL = System.getProperty("test.postgres.url");
    private static final String USER = System.getProperty("test.postgres.username", "test_user");
    private static final String PASSWORD = System.getProperty("test.postgres.password", "test_pass");
    protected static final PostgreSQLContainer<?> postgres;

    static {
        if (EXTERNAL_URL == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("flight_tracker_test_db").withUsername(USER).withPassword(PASSWORD);
            postgres.start();
        } else {
            postgres = null;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EXTERNAL_URL != null ? EXTERNAL_URL : postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    public static boolean isDatabaseReady() {
        if (postgres != null) return postgres.isRunning();
        try (var connection = DriverManager.getConnection(EXTERNAL_URL, USER, PASSWORD)) {
            return connection.isValid(3);
        } catch (java.sql.SQLException e) {
            return false;
        }
    }
}
