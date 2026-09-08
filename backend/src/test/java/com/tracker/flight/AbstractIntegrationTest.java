package com.tracker.flight;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.Socket;

@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static PostgreSQLContainer<?> postgres;
    protected static final boolean IS_LOCAL_POSTGRES_RUNNING = checkLocalPostgres();

    private static boolean checkLocalPostgres() {
        try (Socket socket = new Socket("localhost", 5433)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static {
        if (!IS_LOCAL_POSTGRES_RUNNING) {
            // В CI окружении без запущенного docker-compose поднимаем Testcontainers
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("flight_tracker_test_db")
                    .withUsername("test_user")
                    .withPassword("test_pass");
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (IS_LOCAL_POSTGRES_RUNNING) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/flight_tracker_db");
            registry.add("spring.datasource.username", () -> "tracker_user");
            registry.add("spring.datasource.password", () -> "tracker_secret");
        } else if (postgres != null) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    public static boolean isDatabaseReady() {
        return IS_LOCAL_POSTGRES_RUNNING || (postgres != null && postgres.isRunning());
    }
}
