package com.lucentflow.api.security;

import com.lucentflow.api.config.ConditionalOnApiEnabled;
import com.lucentflow.common.ratelimit.SharedRateLimitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Soft IP rate limit for public platform endpoints (whales / sync-status).
 * Product decision: these routes stay unauthenticated; abuse is throttled instead.
 * Counters are cluster-shared via PostgreSQL minute buckets.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@Component
public class PublicApiRateLimitInterceptor implements HandlerInterceptor {

    private final SharedRateLimitService sharedRateLimitService;
    private int rateLimitPerMinute = 60;

    @Autowired
    public PublicApiRateLimitInterceptor(SharedRateLimitService sharedRateLimitService) {
        this.sharedRateLimitService = sharedRateLimitService;
    }

    PublicApiRateLimitInterceptor(SharedRateLimitService sharedRateLimitService, int rateLimitPerMinute) {
        this.sharedRateLimitService = sharedRateLimitService;
        this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
    }

    @Value("${lucentflow.api.public-rate-limit-per-minute:60}")
    void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (rateLimitPerMinute <= 0) {
            return true;
        }
        String clientKey = resolveClientKey(request);
        if (!sharedRateLimitService.tryAcquire("ip:" + clientKey, rateLimitPerMinute)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Public API rate limit exceeded");
            return false;
        }
        return true;
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
