package com.lucentflow.sdk.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe primary/backup RPC endpoint selection for OkHttp failover.
 * After a failure, routes traffic to the backup URL for {@link #FAILOVER_COOLDOWN_MS}.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
public class RpcEndpointState {

    static final long FAILOVER_COOLDOWN_MS = 5 * 60 * 1000L;

    private final AtomicReference<String> primaryUrlRef;
    private final String backupUrl;

    /** End of failover window (epoch ms); 0 means use primary. */
    private final AtomicLong failoverUntilEpochMs = new AtomicLong(0);

    public RpcEndpointState(String primaryUrl, String backupUrl) {
        if (primaryUrl == null || primaryUrl.isBlank()) {
            throw new IllegalArgumentException("primaryUrl must not be blank");
        }
        this.primaryUrlRef = new AtomicReference<>(primaryUrl.trim());
        this.backupUrl = backupUrl != null && !backupUrl.isBlank() ? backupUrl.trim() : "https://mainnet.base.org";
    }

    /**
     * Clears expired failover windows so the next request may use the primary again.
     */
    public void expireFailoverIfDue() {
        long until = failoverUntilEpochMs.get();
        if (until > 0 && System.currentTimeMillis() >= until) {
            failoverUntilEpochMs.compareAndSet(until, 0);
        }
    }

    /**
     * @return {@code true} while the 5-minute failover window is active (traffic targets {@link #getBackupUrl()}).
     */
    public boolean isFailoverActive() {
        expireFailoverIfDue();
        long until = failoverUntilEpochMs.get();
        return until > 0 && System.currentTimeMillis() < until;
    }

    /**
     * URL to use for the outgoing JSON-RPC HTTP request (full RPC URL including path).
     */
    public String currentRpcUrl() {
        expireFailoverIfDue();
        long until = failoverUntilEpochMs.get();
        if (until > 0 && System.currentTimeMillis() < until) {
            return backupUrl;
        }
        return primaryUrlRef.get();
    }

    /**
     * Invoked when the primary (or current) endpoint fails after OkHttp/Web3j retries.
     * Switches to backup for {@link #FAILOVER_COOLDOWN_MS}.
     */
    public void activateFailoverToBackup() {
        long now = System.currentTimeMillis();
        long next = now + FAILOVER_COOLDOWN_MS;
        for (;;) {
            long until = failoverUntilEpochMs.get();
            if (until > now) {
                return;
            }
            if (failoverUntilEpochMs.compareAndSet(until, next)) {
                log.warn("[FAILOVER] Primary RPC failing. Switching to backup: {}", backupUrl);
                return;
            }
        }
    }

    public String getBackupUrl() {
        return backupUrl;
    }

    public String getPrimaryUrl() {
        return primaryUrlRef.get();
    }
}
