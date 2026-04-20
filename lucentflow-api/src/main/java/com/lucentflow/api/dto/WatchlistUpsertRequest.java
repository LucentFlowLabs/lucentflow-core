package com.lucentflow.api.dto;

/**
 * Request payload for watchlist create/update operations.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record WatchlistUpsertRequest(
        String address,
        String label,
        String category
) {
}
