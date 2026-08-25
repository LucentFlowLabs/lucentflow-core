package com.lucentflow.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Frozen commercial contract for {@code POST /api/v1/risk/score}.
 *
 * @author ArchLucent
 * @since 1.2
 */
public record RiskScoreResponse(
        String riskModelVersion,
        int score,
        int rawScore,
        Map<String, Integer> reasons,
        String address,
        String txHash,
        String bytecodeHash,
        long cloneCount,
        int fundingHops,
        String fundingSourceAddress,
        String fundingSourceTag,
        Boolean blacklistedFunding,
        Long asOfBlock,
        Instant computedAt,
        String coverage,
        String rugRiskLevel
) {
}
