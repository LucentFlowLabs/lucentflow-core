package com.lucentflow.sdk.config;

/**
 * Recommended RPC and pipeline limits derived from {@link RpcProviderType}.
 * Immutable and safe to share across indexer components (e.g. block source and pipeline).
 * <ul>
 *   <li>{@code providerType} — detected {@link RpcProviderType}</li>
 *   <li>{@code recommendedRpcSemaphorePermits} — fair semaphore permits for concurrent RPC</li>
 *   <li>{@code recommendedPipelineChunkSize} — blocks per checkpoint chunk</li>
 *   <li>{@code interBatchSleepMillis} — delay between chunks (0 for professional providers)</li>
 * </ul>
 *
 * @author ArchLucent
 * @since 1.0
 */
public record RpcProviderConfig(
        RpcProviderType providerType,
        int recommendedRpcSemaphorePermits,
        int recommendedPipelineChunkSize,
        long interBatchSleepMillis
) {
}
