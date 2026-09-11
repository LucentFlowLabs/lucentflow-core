package com.lucentflow.common.webhook;

/**
 * One deferred webhook delivery. {@code id} is assigned by PostgreSQL; in-memory rows may omit it.
 *
 * @param id                 store key, {@code null} before insert
 * @param txHash             whale transaction hash
 * @param projectId          tenant id, {@code null} for the operator-global webhook
 * @param webhookUrl         destination URL captured at enqueue (drain may refresh from the project)
 * @param watchlistHit       true when this dispatch was a watchlist hit
 * @param watchlistLabel     optional watchlist label
 * @param watchlistCategory  optional watchlist category
 * @param watchlistAddress   optional watchlist address (lowercase)
 * @param payloadJson        whale risk snapshot JSON
 * @param attempts           DLQ cycles already used
 * @author ArchLucent
 * @since 1.2
 */
public record WebhookDeadLetterRecord(
        Long id,
        String txHash,
        Long projectId,
        String webhookUrl,
        boolean watchlistHit,
        String watchlistLabel,
        String watchlistCategory,
        String watchlistAddress,
        String payloadJson,
        int attempts
) {
}
