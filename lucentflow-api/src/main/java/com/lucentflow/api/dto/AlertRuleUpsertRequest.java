package com.lucentflow.api.dto;

/**
 * Request payload for creating or updating a project alert rule.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record AlertRuleUpsertRequest(
        Integer minRiskScore,
        Boolean watchlistOnly,
        Boolean contractCreationOnly,
        Boolean enabled
) {
}
