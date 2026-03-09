package com.lucentflow;

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

    public static void main(String[] args) {
        SpringApplication.run(LucentFlowApplication.class, args);
    }
}
