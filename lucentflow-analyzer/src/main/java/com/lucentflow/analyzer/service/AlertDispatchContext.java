package com.lucentflow.analyzer.service;

/**
 * Alert metadata shared across outbound providers.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record AlertDispatchContext(
        boolean watchlistHit,
        String watchlistLabel,
        String watchlistCategory,
        String watchlistAddress,
        Long projectId,
        String projectWebhookUrl,
        String projectWebhookSecret
) {
}
