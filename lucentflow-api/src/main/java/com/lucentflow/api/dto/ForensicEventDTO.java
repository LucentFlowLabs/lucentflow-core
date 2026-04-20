package com.lucentflow.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * External-facing DTO for forensic event queries.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ForensicEventDTO(
        String hash,
        Long blockNumber,
        Instant timestamp,
        String fromAddress,
        String toAddress,
        BigDecimal valueEth,
        Integer riskScore,
        String rugRiskLevel,
        String executionStatus,
        Boolean isContractCreation,
        String bytecodeHash,
        Map<String, Integer> riskReasons
) {
}
