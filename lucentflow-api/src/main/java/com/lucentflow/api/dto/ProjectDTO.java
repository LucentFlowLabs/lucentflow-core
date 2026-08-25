package com.lucentflow.api.dto;

import java.time.Instant;

/**
 * API DTO for project metadata. Webhook secret is never echoed; only a configured flag.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ProjectDTO(
        Long id,
        String name,
        String apiKey,
        String webhookUrl,
        boolean webhookSecretConfigured,
        Boolean isActive,
        String plan,
        Integer dailyRequestQuota,
        Integer watchlistLimit,
        Instant createdAt
) {
}
