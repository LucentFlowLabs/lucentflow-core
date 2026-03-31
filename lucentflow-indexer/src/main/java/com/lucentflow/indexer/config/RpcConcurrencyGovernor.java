package com.lucentflow.indexer.config;

import com.lucentflow.sdk.config.RpcProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

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

    private final Semaphore semaphore;
    private final int baselinePermits;
    private final int maxTotalPermits;

    /**
     * Extra permits we injected via {@link Semaphore#release(int)} during catch-up; only that many
     * {@link Semaphore#tryAcquire()} calls may succeed to drain them (idempotent across heartbeats).
     */
    private int bonusOutstanding;

    public RpcConcurrencyGovernor(RpcProviderConfig rpcProviderConfig) {
        this.baselinePermits = rpcProviderConfig.recommendedRpcSemaphorePermits();
        this.maxTotalPermits = Math.min(ALCHEMY_MAX_CONCURRENT_PERMITS, baselinePermits * 2);
        this.semaphore = new Semaphore(baselinePermits, true);
    }

    public void acquire() throws InterruptedException {
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
     * If {@code blockLag} exceeds 1000, release extra permits (up to cap). If lag drops below 10, drain only
     * permits we previously injected (never more than {@link #bonusOutstanding}), so repeated heartbeats
     * cannot over-drain the baseline pool.
     */
    public synchronized void adjustForLag(long blockLag) {
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
