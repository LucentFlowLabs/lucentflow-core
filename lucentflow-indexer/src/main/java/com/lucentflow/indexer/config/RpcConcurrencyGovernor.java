package com.lucentflow.indexer.config;

import lombok.extern.slf4j.Slf4j;
import com.lucentflow.indexer.control.AdaptiveBackpressureController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dynamic fair semaphore for RPC concurrency: doubles effective permits when lag is high,
 * then best-effort drains bonus permits when caught up (Alchemy-safe cap).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class RpcConcurrencyGovernor {

    private static final int ALCHEMY_MAX_CONCURRENT_PERMITS = 20;
    private static final long COOLING_DOWN_WINDOW_MS = 30_000L;

    public enum State {
        NORMAL,
        COOLING_DOWN
    }

    private final Semaphore semaphore;
    private final int baselinePermits;
    private final int maxTotalPermits;
    private final AtomicReference<State> state = new AtomicReference<>(State.NORMAL);
    private final AtomicLong coolingDownUntilMs = new AtomicLong(0L);

    /**
     * Extra permits we injected via {@link Semaphore#release(int)} during catch-up; only that many
     * {@link Semaphore#tryAcquire()} calls may succeed to drain them (idempotent across heartbeats).
     */
    private int bonusOutstanding;

    private final AdaptiveBackpressureController backpressureController;
    private final boolean catchupBoostEnabled;

    public RpcConcurrencyGovernor(@Value("${lucentflow.indexer.max-concurrency:2}") int maxConcurrency,
                                  AdaptiveBackpressureController backpressureController,
                                  @Value("${lucentflow.indexer.catchup-boost-enabled:false}") boolean catchupBoostEnabled) {
        this.baselinePermits = Math.max(1, maxConcurrency);
        this.maxTotalPermits = Math.min(ALCHEMY_MAX_CONCURRENT_PERMITS, baselinePermits * 2);
        this.semaphore = new Semaphore(baselinePermits, true);
        this.backpressureController = backpressureController;
        this.catchupBoostEnabled = catchupBoostEnabled;
    }

    public void acquire() throws InterruptedException {
        refreshCoolingStateIfExpired();
        semaphore.acquire();
    }

    public void release() {
        semaphore.release();
    }

    public int getQueueLength() {
        return semaphore.getQueueLength();
    }

    /**
     * Permits currently available to acquire (baseline pool plus any unreleased catch-up bonus).
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Adaptive backoff hook: call when a 429 / rate-limit is detected.
     *
     * <p>Moves the governor into {@link State#COOLING_DOWN} for 30 seconds and forces effective
     * concurrency down to 1 permit (best-effort). In-flight permits are not revoked, but new
     * acquisitions will be throttled immediately.</p>
     */
    public void onRateLimitExceeded() {
        long until = System.currentTimeMillis() + COOLING_DOWN_WINDOW_MS;
        coolingDownUntilMs.accumulateAndGet(until, Math::max);
        state.set(State.COOLING_DOWN);

        // Link to scheduler pacing + batch size throttling.
        backpressureController.onRateLimitExceeded();

        // Best-effort: clear available permits and leave exactly 1.
        int drained = semaphore.drainPermits();
        semaphore.release(1);
        bonusOutstanding = 0;
        log.warn("[RPC-GOV] Rate limit detected. Entering COOLING_DOWN for {}ms (drained {}, baseline {}).",
                COOLING_DOWN_WINDOW_MS, drained, baselinePermits);
    }

    private void refreshCoolingStateIfExpired() {
        if (state.get() != State.COOLING_DOWN) return;
        long now = System.currentTimeMillis();
        long until = coolingDownUntilMs.get();
        if (now < until) return;

        // Restore baseline permits (best-effort); keep fairness.
        state.set(State.NORMAL);
        int drained = semaphore.drainPermits();
        semaphore.release(baselinePermits);
        bonusOutstanding = 0;
        log.info("[RPC-GOV] Cooling window expired. Restored NORMAL concurrency (baseline {}, drained {}).",
                baselinePermits, drained);
    }

    /**
     * If {@code blockLag} exceeds 1000, release extra permits (up to cap). If lag drops below 10, drain only
     * permits we previously injected (never more than {@link #bonusOutstanding}), so repeated heartbeats
     * cannot over-drain the baseline pool.
     */
    public synchronized void adjustForLag(long blockLag) {
        refreshCoolingStateIfExpired();
        if (state.get() == State.COOLING_DOWN) {
            return;
        }
        if (!catchupBoostEnabled) {
            return;
        }
        if (blockLag > 1000L && bonusOutstanding == 0) {
            int extra = Math.min(baselinePermits, maxTotalPermits - baselinePermits);
            if (extra > 0) {
                semaphore.release(extra);
                bonusOutstanding = extra;
                log.info("[CATCH-UP] blockLag={} > 1000; releasing {} extra RPC permits (cap ~{})",
                        blockLag, extra, maxTotalPermits);
            }
        } else if (blockLag < 10L && bonusOutstanding > 0) {
            int drained = 0;
            while (bonusOutstanding > 0 && semaphore.tryAcquire()) {
                bonusOutstanding--;
                drained++;
            }
            log.info("[CATCH-UP] blockLag={} < 10; drained {} bonus permits (best-effort)", blockLag, drained);
            if (bonusOutstanding > 0) {
                log.debug("[CATCH-UP] {} bonus permits still pending drain (held by in-flight RPC)", bonusOutstanding);
            }
        }
    }
}
