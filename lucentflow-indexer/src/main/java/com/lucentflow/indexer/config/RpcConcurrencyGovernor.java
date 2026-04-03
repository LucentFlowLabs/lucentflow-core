package com.lucentflow.indexer.config;

import com.lucentflow.indexer.control.AdaptiveBackpressureController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dynamic fair concurrency gate for RPC: baseline permits follow {@link IndexerRpcProfile}
 * (Alchemy vs public failover), optional catch-up boost when lag is high, and a cooling-down
 * window after HTTP 429 (mirrors prior Semaphore semantics: at most one additional permit beyond
 * in-flight work when cooling starts).
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

    private final AdaptiveBackpressureController backpressureController;
    private final IndexerRpcProfile profile;

    private final AtomicReference<State> state = new AtomicReference<>(State.NORMAL);
    private final AtomicLong coolingDownUntilMs = new AtomicLong(0L);

    private final ReentrantLock gate = new ReentrantLock(true);
    private final Condition notFull = gate.newCondition();
    private int inflight;

    /**
     * Extra capacity from catch-up boost (same meaning as legacy {@code bonusOutstanding}).
     */
    private int bonusOutstanding;

    /**
     * When {@link State#COOLING_DOWN}: max concurrent RPCs (set to {@code inflight + 1} at 429).
     */
    private int coolingCap = -1;

    public RpcConcurrencyGovernor(AdaptiveBackpressureController backpressureController,
                                  IndexerRpcProfile profile) {
        this.backpressureController = backpressureController;
        this.profile = profile;
    }

    public void acquire() throws InterruptedException {
        refreshCoolingStateIfExpired();
        gate.lock();
        try {
            while (inflight >= effectiveCap()) {
                notFull.await();
            }
            inflight++;
        } finally {
            gate.unlock();
        }
    }

    public void release() {
        gate.lock();
        try {
            if (inflight > 0) {
                inflight--;
            }
            notFull.signalAll();
        } finally {
            gate.unlock();
        }
    }

    /**
     * Legacy hook; blocked threads wait on a {@link java.util.concurrent.locks.Condition} (length not exposed portably).
     */
    public int getQueueLength() {
        return 0;
    }

    /**
     * Permits available for new acquires (best-effort snapshot).
     */
    public int availablePermits() {
        gate.lock();
        try {
            return Math.max(0, effectiveCap() - inflight);
        } finally {
            gate.unlock();
        }
    }

    public int getBaselinePermits() {
        return profile.effectiveMaxConcurrency();
    }

    public boolean isCatchupBoostEnabled() {
        return profile.effectiveCatchupBoostEnabled();
    }

    /**
     * Adaptive backoff hook: call when a 429 / rate-limit is detected.
     */
    public void onRateLimitExceeded() {
        long until = System.currentTimeMillis() + COOLING_DOWN_WINDOW_MS;
        coolingDownUntilMs.accumulateAndGet(until, Math::max);
        state.set(State.COOLING_DOWN);

        backpressureController.onRateLimitExceeded();

        gate.lock();
        try {
            coolingCap = inflight + 1;
            bonusOutstanding = 0;
            log.warn("[RPC-GOV] Rate limit detected. Entering COOLING_DOWN for {}ms (inflight={}, coolingCap={}).",
                    COOLING_DOWN_WINDOW_MS, inflight, coolingCap);
        } finally {
            gate.unlock();
        }
    }

    private void refreshCoolingStateIfExpired() {
        if (state.get() != State.COOLING_DOWN) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = coolingDownUntilMs.get();
        if (now < until) {
            return;
        }

        gate.lock();
        try {
            if (state.get() != State.COOLING_DOWN) {
                return;
            }
            state.set(State.NORMAL);
            coolingCap = -1;
            bonusOutstanding = 0;
            log.info("[RPC-GOV] Cooling window expired. Restored NORMAL concurrency (baseline {}).",
                    profile.effectiveMaxConcurrency());
            notFull.signalAll();
        } finally {
            gate.unlock();
        }
    }

    private int effectiveCap() {
        if (state.get() == State.COOLING_DOWN && coolingCap > 0) {
            return coolingCap;
        }
        int baseline = profile.effectiveMaxConcurrency();
        if (!profile.effectiveCatchupBoostEnabled()) {
            return baseline;
        }
        return Math.min(ALCHEMY_MAX_CONCURRENT_PERMITS, baseline + bonusOutstanding);
    }

    /**
     * If {@code blockLag} exceeds 1000, release extra permits (up to cap). If lag drops below 10, drain only
     * permits we previously injected.
     */
    public void adjustForLag(long blockLag) {
        refreshCoolingStateIfExpired();
        if (state.get() == State.COOLING_DOWN) {
            return;
        }
        if (!profile.effectiveCatchupBoostEnabled()) {
            return;
        }
        gate.lock();
        try {
            int baseline = profile.effectiveMaxConcurrency();
            int maxTotal = Math.min(ALCHEMY_MAX_CONCURRENT_PERMITS, baseline * 2);
            if (blockLag > 1000L && bonusOutstanding == 0) {
                int extra = Math.min(baseline, maxTotal - baseline);
                if (extra > 0) {
                    bonusOutstanding = extra;
                    log.info("[CATCH-UP] blockLag={} > 1000; releasing {} extra RPC permits (cap ~{})",
                            blockLag, extra, maxTotal);
                    notFull.signalAll();
                }
            } else if (blockLag < 10L && bonusOutstanding > 0) {
                int cleared = bonusOutstanding;
                bonusOutstanding = 0;
                log.info("[CATCH-UP] blockLag={} < 10; cleared {} bonus capacity", blockLag, cleared);
                notFull.signalAll();
            }
        } finally {
            gate.unlock();
        }
    }
}
