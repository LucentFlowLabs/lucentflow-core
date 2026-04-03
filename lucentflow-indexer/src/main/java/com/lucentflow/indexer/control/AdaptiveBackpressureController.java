package com.lucentflow.indexer.control;

import com.lucentflow.indexer.config.IndexerRpcProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Small adaptive controller that links rate-limit signals (HTTP 429) to scheduler pacing
 * and batch sizing. Base pacing follows {@link IndexerRpcProfile} (Alchemy vs public failover).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class AdaptiveBackpressureController {

    private final IndexerRpcProfile profile;

    private final AtomicLong coolingDownUntilMs = new AtomicLong(0L);
    private final AtomicLong nextScanAllowedAtMs = new AtomicLong(0L);

    public AdaptiveBackpressureController(IndexerRpcProfile profile) {
        this.profile = profile;
    }

    /**
     * Called when an RPC 429 is detected. Extends the cooling window (monotonic).
     */
    public void onRateLimitExceeded() {
        long now = System.currentTimeMillis();
        long window = profile.effectiveCooldownWindowMs();
        long until = now + window;
        coolingDownUntilMs.accumulateAndGet(until, Math::max);

        long nextAllowed = now + profile.effectiveCooldownPollingIntervalMs();
        nextScanAllowedAtMs.accumulateAndGet(nextAllowed, Math::max);

        log.warn("[BACKPRESSURE] 429 detected. Cooling down for {}ms: polling={}ms, maxBatchSize={}",
                window, profile.effectiveCooldownPollingIntervalMs(), profile.effectiveCooldownMaxBatchSize());
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
        long interval = isCoolingDown()
                ? profile.effectiveCooldownPollingIntervalMs()
                : profile.effectivePollingIntervalMs();
        long next = now + Math.max(0L, interval);
        nextScanAllowedAtMs.accumulateAndGet(next, Math::max);
    }

    public long effectiveMaxBatchSize() {
        return isCoolingDown() ? profile.effectiveCooldownMaxBatchSize() : profile.effectiveMaxBatchSizeCap();
    }

    public long basePollingIntervalMs() {
        return profile.effectivePollingIntervalMs();
    }
}
