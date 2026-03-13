package com.lucentflow.indexer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for HTTP clients and external API integrations.
 */
@Configuration
public class HttpClientConfig {
    
    /**
     * RestTemplate for Basescan API calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
