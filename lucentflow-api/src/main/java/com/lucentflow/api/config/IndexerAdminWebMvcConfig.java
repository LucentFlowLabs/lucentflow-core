package com.lucentflow.api.config;

import com.lucentflow.api.security.AdminKeyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@code X-Admin-Key} for indexer-only admin routes when the product API
 * is disabled (worker profile). Monolith / API replicas use {@link WebMvcConfig}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${lucentflow.runtime.enable-indexer:true}' == 'true' and '${lucentflow.runtime.enable-api:true}' == 'false'")
public class IndexerAdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminKeyInterceptor adminKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminKeyInterceptor)
                .addPathPatterns("/api/v1/admin/backfill", "/api/v1/admin/backfill/**");
    }
}
