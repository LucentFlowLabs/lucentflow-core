package com.lucentflow.api.dto;

import java.time.Instant;

/**
 * API DTO for per-project alert rule configuration.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record AlertRuleDTO(
        Long id,
        Long projectId,
        Integer minRiskScore,
        Boolean watchlistOnly,
        Boolean contractCreationOnly,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
