package com.lucentflow.api.config;

import com.lucentflow.api.config.ConditionalOnApiEnabled;

import com.lucentflow.api.security.AdminKeyInterceptor;
import com.lucentflow.api.security.ApiKeyInterceptor;
import com.lucentflow.api.security.PublicApiRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers API key based project scoping interceptors.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor apiKeyInterceptor;
    private final AdminKeyInterceptor adminKeyInterceptor;
    private final PublicApiRateLimitInterceptor publicApiRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminKeyInterceptor)
                .addPathPatterns("/api/v1/admin/**");

        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns(
                        "/api/v1/watchlist/**",
                        "/api/v1/forensics/**",
                        "/api/v1/alert-rules/**",
                        "/api/v1/usage/**",
                        "/api/v1/risk/**");

        // Platform free tier: public whales/sync remain unauthenticated, soft-throttled by IP.
        registry.addInterceptor(publicApiRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/whales",
                        "/api/v1/whales/**",
                        "/api/v1/sync-status",
                        "/api/v1/oracle/**");
    }
}
