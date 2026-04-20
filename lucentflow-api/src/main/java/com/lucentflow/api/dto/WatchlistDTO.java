package com.lucentflow.api.dto;

import java.time.Instant;

/**
 * API DTO for watchlist entries.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record WatchlistDTO(
        Long id,
        Long projectId,
        String address,
        String label,
        String category,
        Instant createdAt
) {
}
