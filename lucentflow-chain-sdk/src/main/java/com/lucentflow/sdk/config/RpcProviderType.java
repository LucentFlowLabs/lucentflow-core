package com.lucentflow.sdk.config;

/**
 * Classifies the configured JSON-RPC endpoint for throughput and throttling decisions.
 *
 * @author ArchLucent
 * @since 1.0
 */
public enum RpcProviderType {

    /**
     * Paid / dedicated infrastructure (higher concurrency and larger pipeline chunks).
     */
    PROFESSIONAL,

    /**
     * Shared public endpoint; conservative limits and inter-batch delays apply.
     */
    PUBLIC;

    /**
     * Detects provider category from the RPC URL (case-insensitive substring match).
     *
     * @param rpcUrl configured {@code lucentflow.chain.rpc-url}
     * @return {@link #PROFESSIONAL} if URL matches a known vendor pattern, else {@link #PUBLIC}
     */
    public static RpcProviderType fromRpcUrl(String rpcUrl) {
        if (rpcUrl == null || rpcUrl.isBlank()) {
            return PUBLIC;
        }
        String u = rpcUrl.toLowerCase();
        return switch (u) {
            case String s when s.contains("alchemy") -> PROFESSIONAL;
            case String s when s.contains("quicknode") -> PROFESSIONAL;
            case String s when s.contains("infura") -> PROFESSIONAL;
            case String s when s.contains("blastapi") -> PROFESSIONAL;
            case String s when s.contains("ankr") -> PROFESSIONAL;
            default -> PUBLIC;
        };
    }
}
