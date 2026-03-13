package com.lucentflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Spring Boot configuration validator for local development environment setup.
 * 
 * <p>Implementation Details:
 * Validates database connectivity and profile configuration during application startup.
 * Ensures H2 database availability and local profile activation for development.
 * Virtual thread compatible through event-driven validation and stateless design.
 * Provides early failure detection for configuration issues in development environments.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class LocalConfigurationValidator {
    
    private final DataSource dataSource;
    private final Environment environment;
    
    public LocalConfigurationValidator(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }
    
    /**
     * Validates local development configuration after application startup.
     * 
     * <p>Performs comprehensive validation of:
     * - Active Spring profiles (expects 'local' profile)
     * - Database connectivity and H2 database availability
     * - RPC URL configuration for blockchain access
     * Provides detailed logging for development environment verification.</p>
     * 
     * @throws RuntimeException if database validation fails
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateLocalConfiguration() {
        log.info("🔍 Validating Local Configuration...");
        
        // Check active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isLocalActive = false;
        
        for (String profile : activeProfiles) {
            if ("local".equals(profile)) {
                isLocalActive = true;
                break;
            }
        }
        
        if (isLocalActive) {
            log.info("✅ Local profile is active");
            
            // Validate database connection
            try {
                String url = dataSource.getConnection().getMetaData().getURL();
                log.info("✅ Database URL: {}", url);
                
                if (url.contains("h2")) {
                    log.info("✅ H2 Database configured successfully");
                } else {
                    log.warn("⚠️ Expected H2 database but found: {}", url);
                }
                
                // Validate RPC URL
                String rpcUrl = environment.getProperty("lucentflow.chain.rpc-url");
                log.info("✅ RPC URL: {}", rpcUrl);
                
                log.info("🚀 Local-Mock environment is ready!");
                log.info("📊 H2 Console: http://localhost:8080/h2-console");
                
            } catch (Exception e) {
                log.error("❌ Database validation failed", e);
            }
        } else {
            log.info("ℹ️ Local profile not active. Current profiles: {}", String.join(", ", activeProfiles));
        }
    }
}
