package com.lucentflow;

import com.lucentflow.common.config.AdaptiveEnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for LucentFlow blockchain indexing platform.
 * 
 * Key configurations:
 * - @SpringBootApplication: Enables Spring Boot auto-configuration
 * - @EnableScheduling: Enables scheduled tasks in BlockchainPipelineService
 * - @EnableAsync: Enables async processing for whale analysis handlers
 * - Component scan: Explicitly scans com.lucentflow package for all modules
 * 
 * @author ArchLucent
 * @since 1.0
 */
@SpringBootApplication(scanBasePackages = "com.lucentflow")
@EnableScheduling
@EnableAsync
public class LucentFlowApplication {

    private static final Logger log = LoggerFactory.getLogger(LucentFlowApplication.class);

    /**
     * Pre-initialize web3j Async to avoid IllegalStateException during shutdown hooks registration
     */
    static {
        // Pre-initialize web3j Async to avoid IllegalStateException during shutdown hooks registration
        try {
            Class.forName("org.web3j.utils.Async");
        } catch (ClassNotFoundException ignored) {}
    }

    public static void main(String[] args) {
        AdaptiveEnvLoader.loadEnv();

        // Fail-fast security guard for Java 21 (.env may have set system properties only)
        String postgresPassword = resolvePostgresPasswordFromEnvOrProperties();
        if (postgresPassword == null || postgresPassword.trim().isEmpty()) {
            log.error("\n\n🚨 SECURITY VIOLATION: POSTGRES_PASSWORD environment variable is not set!");
            log.error("   LucentFlow cannot start without secure database credentials.");
            log.error("   Please set POSTGRES_PASSWORD in your environment or .env file.");
            log.error("   This is a security measure to prevent unauthorized database access.\n");
            System.exit(1);
        }
        
        log.info("🔐 Security check passed - POSTGRES_PASSWORD is configured");
        SpringApplication.run(LucentFlowApplication.class, args);
    }

    private static String resolvePostgresPasswordFromEnvOrProperties() {
        String fromEnv = System.getenv("POSTGRES_PASSWORD");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv;
        }
        return System.getProperty("POSTGRES_PASSWORD");
    }
}
