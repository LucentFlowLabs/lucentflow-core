package com.lucentflow.indexer;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;
import org.flywaydb.core.Flyway;
import org.web3j.protocol.Web3j;

/**
 * Local test bootstrapper for the lucentflow-indexer module.
 * This prevents the indexer from trying to load components from other modules.
 */
@Slf4j
@SpringBootApplication
public class TestIndexerApplication {

    /**
     * Pre-initialize web3j Async to avoid IllegalStateException during shutdown hooks registration
     */
    static {
        // Disable Web3j Async keepalive to prevent non-daemon threads
        System.setProperty("org.web3j.client.async.keepalive", "false");
        
        // Pre-initialize web3j Async to avoid IllegalStateException during shutdown hooks registration
        try {
            Class.forName("org.web3j.utils.Async");
        } catch (Exception ignored) {}
    }

    /**
     * Test configuration for providing mock beans.
     */
    @TestConfiguration
    public static class TestConfig {
        
        /**
         * Mock Flyway bean for tests to satisfy @DependsOn requirements.
         */
        @Bean
        @Primary
        public Flyway flyway() {
            return Mockito.mock(Flyway.class);
        }
        
        /**
         * Mock Web3j bean for tests to satisfy dependency requirements.
         */
        @Bean
        @Primary
        public Web3j web3j() {
            return Mockito.mock(Web3j.class);
        }
    }

    /**
     * T10 Standard: Nuclear shutdown hook to prevent 30s JVM hang
     */
    @PreDestroy
    public void emergencyHalt() {
        log.info("Test Context Closing: Executing Nuclear Halt to prevent 30s hang.");
        // Halt is more aggressive than exit(0), it doesn't wait for hooks
        Runtime.getRuntime().halt(0);
    }
}
