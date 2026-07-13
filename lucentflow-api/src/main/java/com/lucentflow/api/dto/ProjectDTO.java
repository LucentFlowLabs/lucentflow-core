package com.lucentflow.api.dto;

import java.time.Instant;

/**
 * API DTO for project metadata.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ProjectDTO(
        Long id,
        String name,
        String apiKey,
        String webhookUrl,
        Boolean isActive,
        Instant createdAt
) {
}
