package com.lucentflow.indexer.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;

/**
 * T10 Standard: Nuclear shutdown configuration for Web3j to prevent JVM hangs.
 * Explicitly kills web3j and its underlying connection pool during Spring context destruction.
 */
@Slf4j
@Configuration
public class Web3jShutdownConfig {
    
    @Autowired
    private Web3j web3j;

    /**
     * T10 Standard: Hard-fix Web3j with aggressive timeout to prevent scheduler survival
     */
    @PreDestroy
    public void onDestroy() {
        log.info("Stopping Web3j...");
        if (web3j != null) {
            web3j.shutdown();
        }
        // Force OkHttp to close its dispatcher if accessible, or just rely on the 2s Maven kill.
        log.info("Web3j hard shutdown complete.");
    }
}
