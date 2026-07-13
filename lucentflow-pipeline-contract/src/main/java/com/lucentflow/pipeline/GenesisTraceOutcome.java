package com.lucentflow.pipeline;

/**
 * Outcome of Genesis Trace (recursive funding hops).
 *
 * @param fundingSourceAddress ultimate funder after hops (may be null)
 * @param fundingSourceTag     human-readable classification tag
 * @param blacklisted          true if funder matches blacklist / mixer heuristics
 * @param layersTraced         hop count returned by SQL (0 if no row)
 * @author ArchLucent
 * @since 1.2
 */
public record GenesisTraceOutcome(
        String fundingSourceAddress,
        String fundingSourceTag,
        boolean blacklisted,
        int layersTraced
) {
}
