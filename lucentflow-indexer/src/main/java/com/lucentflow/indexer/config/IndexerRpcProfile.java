package com.lucentflow.indexer.config;

import com.lucentflow.sdk.config.RpcEndpointState;
import com.lucentflow.sdk.config.RpcProviderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Convention-over-configuration indexer pacing: official Base public RPC ({@code mainnet.base.org})
 * uses fixed safe defaults (batch 100, concurrency 1, interval 3000ms). Environment overrides apply
 * only when {@code lucentflow.chain.rpc-url} is <strong>not</strong> the official host. When HTTP
 * failover routes to an official backup URL, .env pacing is ignored and the same hardcoded public
 * defaults apply.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Component
public class IndexerRpcProfile {

    /** Hardcoded safe pacing for official Base public RPC (mainnet.base.org). */
    public static final long OFFICIAL_POLLING_INTERVAL_MS = 3000L;
    public static final long OFFICIAL_MAX_BATCH_SIZE = 100L;
    public static final int OFFICIAL_MAX_CONCURRENCY = 1;
    public static final long OFFICIAL_INTER_BATCH_SLEEP_MS = 3000L;
    public static final boolean OFFICIAL_CATCHUP_BOOST_ENABLED = false;
    public static final long OFFICIAL_COOLDOWN_WINDOW_MS = 30_000L;
    public static final long OFFICIAL_COOLDOWN_POLLING_INTERVAL_MS = 15_000L;
    public static final long OFFICIAL_COOLDOWN_MAX_BATCH_SIZE = 40L;

    private final RpcEndpointState endpointState;
    private final String primaryRpcUrl;

    private final long envPollingIntervalMs;
    private final long envMaxBatchSize;
    private final int envMaxConcurrency;
    private final boolean envCatchupBoostEnabled;
    private final long envCooldownWindowMs;
    private final long envCooldownPollingIntervalMs;
    private final long envCooldownMaxBatchSize;

    public IndexerRpcProfile(
            RpcEndpointState endpointState,
            @Value("${lucentflow.chain.rpc-url:}") String primaryRpcUrl,
            @Value("${lucentflow.indexer.polling-interval-ms:2000}") long envPollingIntervalMs,
            @Value("${lucentflow.indexer.max-batch-size:200}") long envMaxBatchSize,
            @Value("${lucentflow.indexer.max-concurrency:2}") int envMaxConcurrency,
            @Value("${lucentflow.indexer.catchup-boost-enabled:false}") boolean envCatchupBoostEnabled,
            @Value("${lucentflow.indexer.cooldown.window-ms:30000}") long envCooldownWindowMs,
            @Value("${lucentflow.indexer.cooldown.polling-interval-ms:10000}") long envCooldownPollingIntervalMs,
            @Value("${lucentflow.indexer.cooldown.max-batch-size:20}") long envCooldownMaxBatchSize) {
        this.endpointState = endpointState;
        this.primaryRpcUrl = primaryRpcUrl != null ? primaryRpcUrl : "";
        this.envPollingIntervalMs = Math.max(0L, envPollingIntervalMs);
        this.envMaxBatchSize = Math.max(1L, envMaxBatchSize);
        this.envMaxConcurrency = Math.max(1, envMaxConcurrency);
        this.envCatchupBoostEnabled = envCatchupBoostEnabled;
        this.envCooldownWindowMs = Math.max(1_000L, envCooldownWindowMs);
        this.envCooldownPollingIntervalMs = Math.max(1_000L, envCooldownPollingIntervalMs);
        this.envCooldownMaxBatchSize = Math.max(1L, envCooldownMaxBatchSize);
    }

    /**
     * @return {@code true} when indexer must ignore .env pacing and use {@link #OFFICIAL_*} constants:
     *         official primary URL, or active failover to an official backup URL.
     */
    public boolean usesOfficialHardcodedPacing() {
        return forceOfficialHardcodedPacing();
    }

    private boolean forceOfficialHardcodedPacing() {
        if (OfficialBaseRpcPolicy.isOfficialBasePublicRpc(primaryRpcUrl)) {
            return true;
        }
        return endpointState.isFailoverActive()
                && OfficialBaseRpcPolicy.isOfficialBasePublicRpc(endpointState.getBackupUrl());
    }

    public long effectivePollingIntervalMs() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_POLLING_INTERVAL_MS : envPollingIntervalMs;
    }

    public long effectiveMaxBatchSizeCap() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_MAX_BATCH_SIZE : envMaxBatchSize;
    }

    public int effectiveMaxConcurrency() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_MAX_CONCURRENCY : envMaxConcurrency;
    }

    public boolean effectiveCatchupBoostEnabled() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_CATCHUP_BOOST_ENABLED : envCatchupBoostEnabled;
    }

    public long effectiveCooldownWindowMs() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_COOLDOWN_WINDOW_MS : envCooldownWindowMs;
    }

    public long effectiveCooldownPollingIntervalMs() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_COOLDOWN_POLLING_INTERVAL_MS : envCooldownPollingIntervalMs;
    }

    public long effectiveCooldownMaxBatchSize() {
        return forceOfficialHardcodedPacing() ? OFFICIAL_COOLDOWN_MAX_BATCH_SIZE : envCooldownMaxBatchSize;
    }

    /**
     * Inter-batch sleep between checkpoint chunks (orchestrator still enforces at least 500ms).
     */
    public long effectiveInterBatchSleepMillis(RpcProviderConfig primaryTierConfig) {
        if (forceOfficialHardcodedPacing()) {
            return OFFICIAL_INTER_BATCH_SLEEP_MS;
        }
        return primaryTierConfig.interBatchSleepMillis();
    }

    /**
     * Pipeline chunk size: min(provider chunk, user max-batch cap); official pacing caps at {@link #OFFICIAL_MAX_BATCH_SIZE}.
     */
    public long effectivePipelineChunkSize(RpcProviderConfig primaryTierConfig) {
        long userCap = effectiveMaxBatchSizeCap();
        if (forceOfficialHardcodedPacing()) {
            return Math.max(1L, Math.min(OFFICIAL_MAX_BATCH_SIZE, userCap));
        }
        long providerChunk = primaryTierConfig.recommendedPipelineChunkSize();
        return Math.max(1L, Math.min(providerChunk, userCap));
    }
}
