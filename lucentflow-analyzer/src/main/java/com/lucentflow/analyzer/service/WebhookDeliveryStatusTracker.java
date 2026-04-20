package com.lucentflow.analyzer.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks webhook delivery health for project-scoped monitoring.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Component
public class WebhookDeliveryStatusTracker {

    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final ConcurrentHashMap<Long, Instant> projectLastFailureAt = new ConcurrentHashMap<>();
    private volatile Instant lastFailureAt;

    public void markSuccess(Long projectId) {
        successCount.incrementAndGet();
    }

    public void markFailure(Long projectId) {
        failureCount.incrementAndGet();
        Instant now = Instant.now();
        lastFailureAt = now;
        if (projectId != null) {
            projectLastFailureAt.put(projectId, now);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                successCount.get(),
                failureCount.get(),
                lastFailureAt,
                Map.copyOf(projectLastFailureAt)
        );
    }

    public record Snapshot(
            long successCount,
            long failureCount,
            Instant lastFailureAt,
            Map<Long, Instant> projectLastFailureAt
    ) {
    }
}
