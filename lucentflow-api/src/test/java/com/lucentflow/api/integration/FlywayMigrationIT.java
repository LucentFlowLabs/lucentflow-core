package com.lucentflow.api.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies classpath Flyway scripts (V1–V21+) against a real Postgres 16 container.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
class FlywayMigrationIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lucentflow")
            .withUsername("lucent")
            .withPassword("lucent");

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Test
    void migratesThroughV21SharedRateLimitAndWorkerLease() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("21");

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "projects", "webhook_secret")) {
                assertThat(rs.next()).as("projects.webhook_secret exists").isTrue();
            }
            try (ResultSet rs = connection.getMetaData().getTables(null, null, "worker_leases", null)) {
                assertThat(rs.next()).as("worker_leases exists").isTrue();
            }
            try (ResultSet rs = connection.getMetaData().getTables(null, null, "api_rate_limit_buckets", null)) {
                assertThat(rs.next()).as("api_rate_limit_buckets exists").isTrue();
            }
        }
    }
}
