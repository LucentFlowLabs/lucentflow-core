package com.lucentflow.api.security;

import com.lucentflow.api.config.ConditionalOnApiEnabled;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Soft IP rate limit for public platform endpoints (whales / sync-status).
 * Product decision: these routes stay unauthenticated; abuse is throttled instead.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@Component
public class PublicApiRateLimitInterceptor implements HandlerInterceptor {

    private final Clock clock;
    private final ConcurrentHashMap<String, MinuteWindow> windows = new ConcurrentHashMap<>();
    private int rateLimitPerMinute = 60;

    public PublicApiRateLimitInterceptor() {
        this.clock = Clock.systemUTC();
    }

    PublicApiRateLimitInterceptor(int rateLimitPerMinute, Clock clock) {
        this.clock = clock;
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
        if (!tryAcquire(clientKey)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Public API rate limit exceeded");
            return false;
        }
        return true;
    }

    private boolean tryAcquire(String clientKey) {
        long epochMinute = Instant.now(clock).getEpochSecond() / 60L;
        MinuteWindow window = windows.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.epochMinute != epochMinute) {
                return new MinuteWindow(epochMinute, new AtomicInteger(0));
            }
            return existing;
        });
        return window.count.incrementAndGet() <= rateLimitPerMinute;
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private record MinuteWindow(long epochMinute, AtomicInteger count) {
    }
}
