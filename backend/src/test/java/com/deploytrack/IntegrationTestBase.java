package com.deploytrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

// Shared base for tests that exercise the real HTTP stack: routing, JSON
// binding, validation, the security filter chain, and actual SQL against a
// real PostgreSQL container.
//
// The container is static so a single Postgres instance is reused across every
// test class in the run -- starting one per class would add ~5s each.
// @ServiceConnection wires its dynamic URL, username and password into Spring
// automatically, replacing the datasource settings in application.yml.
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        // Bootstrap an admin so tests can exercise admin-only paths without
        // reaching into the database. Deliberately different credentials from
        // any real deployment.
        registry.add("deploytrack.bootstrap.admin-email", () -> "admin@test.local");
        registry.add("deploytrack.bootstrap.admin-username", () -> "admin");
        registry.add("deploytrack.bootstrap.admin-password", () -> "test-admin-password");
    }
}
