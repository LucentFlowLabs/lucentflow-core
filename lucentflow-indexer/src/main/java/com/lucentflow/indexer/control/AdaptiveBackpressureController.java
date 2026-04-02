package com.lucentflow.indexer.control;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Small adaptive controller that links rate-limit signals (HTTP 429) to scheduler pacing
 * and batch sizing.
 *
 * <p>Design goals:
 * - Never advance checkpoints during RPC storms (handled by orchestrator chunk success gating).
 * - When 429 is detected: immediately slow down scan cadence and reduce max batch size.
 * - Recover automatically after the cooling-down window expires.</p>
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class AdaptiveBackpressureController {
    private final long basePollingIntervalMs;
    private final long baseMaxBatchSize;
    private final long cooldownWindowMs;
    private final long cooldownPollingIntervalMs;
    private final long cooldownMaxBatchSize;

    private final AtomicLong coolingDownUntilMs = new AtomicLong(0L);
    private final AtomicLong nextScanAllowedAtMs = new AtomicLong(0L);

    public AdaptiveBackpressureController(
            @Value("${lucentflow.indexer.polling-interval-ms:2000}") long pollingIntervalMs,
            @Value("${lucentflow.indexer.max-batch-size:200}") long maxBatchSize,
            @Value("${lucentflow.indexer.cooldown.window-ms:30000}") long cooldownWindowMs,
            @Value("${lucentflow.indexer.cooldown.polling-interval-ms:10000}") long cooldownPollingIntervalMs,
            @Value("${lucentflow.indexer.cooldown.max-batch-size:20}") long cooldownMaxBatchSize) {
        this.basePollingIntervalMs = Math.max(0L, pollingIntervalMs);
        this.baseMaxBatchSize = Math.max(1L, maxBatchSize);

        // Safety floor/ceiling: never allow 0/negative values to break pacing.
        this.cooldownWindowMs = Math.max(1_000L, cooldownWindowMs);
        this.cooldownPollingIntervalMs = Math.max(1_000L, cooldownPollingIntervalMs);
        this.cooldownMaxBatchSize = Math.max(1L, cooldownMaxBatchSize);
    }

    /**
     * Called when an RPC 429 is detected. Extends the cooling window (monotonic).
     */
    public void onRateLimitExceeded() {
        long now = System.currentTimeMillis();
        long until = now + cooldownWindowMs;
        coolingDownUntilMs.accumulateAndGet(until, Math::max);

        long nextAllowed = now + cooldownPollingIntervalMs;
        nextScanAllowedAtMs.accumulateAndGet(nextAllowed, Math::max);

        log.warn("[BACKPRESSURE] 429 detected. Cooling down for {}ms: polling={}ms, maxBatchSize={}",
                cooldownWindowMs, cooldownPollingIntervalMs, cooldownMaxBatchSize);
    }

    public boolean isCoolingDown() {
        return System.currentTimeMillis() < coolingDownUntilMs.get();
    }

    /**
     * Returns false when the scheduler should skip this tick (too soon).
     */
    public boolean allowScanNow() {
        long now = System.currentTimeMillis();
        long allowedAt = nextScanAllowedAtMs.get();
        return now >= allowedAt;
    }

    /**
     * Should be invoked when a scan cycle begins successfully (after lock acquisition).
     * Advances next allowed scan timestamp based on current state.
     */
    public void markScanStarted() {
        long now = System.currentTimeMillis();
        long interval = isCoolingDown() ? cooldownPollingIntervalMs : basePollingIntervalMs;
        long next = now + Math.max(0L, interval);
        nextScanAllowedAtMs.accumulateAndGet(next, Math::max);
    }

    public long effectiveMaxBatchSize() {
        return isCoolingDown() ? cooldownMaxBatchSize : baseMaxBatchSize;
    }

    public long basePollingIntervalMs() {
        return basePollingIntervalMs;
    }
}

