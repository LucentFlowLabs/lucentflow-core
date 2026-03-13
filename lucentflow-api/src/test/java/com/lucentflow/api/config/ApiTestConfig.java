package com.lucentflow.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for API module tests.
 * Provides mock beans to satisfy dependencies during testing.
 */
@TestConfiguration
public class ApiTestConfig {
    
    /**
     * Mock Flyway bean for tests to satisfy @DependsOn requirements.
     */
    @Bean
    @Primary
    public Flyway mockFlyway() {
        // Return a minimal Flyway instance that won't actually run migrations
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:testdb", "sa", "")
                .load();
    }
}
