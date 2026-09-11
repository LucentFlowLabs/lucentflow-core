package com.lucentflow.common.webhook;

import java.util.List;

/**
 * Durable or in-memory queue for webhook deliveries that missed the live bulkhead.
 *
 * @author ArchLucent
 * @since 1.2
 */
public interface WebhookDeadLetterStore {

    /**
     * Persist a deferred delivery. Returns {@code false} when the store is at capacity.
     *
     * @param record row to insert; {@code id} is ignored
     * @return {@code true} if the row was accepted
     */
    boolean enqueue(WebhookDeadLetterRecord record);

    /**
     * Remove and return up to {@code budget} oldest rows (queue poll). A crash after poll
     * and before a successful HTTP send is at-most-once for that batch; unpolled rows survive restart.
     *
     * @param budget max rows to claim
     * @return FIFO batch, possibly empty
     */
    List<WebhookDeadLetterRecord> pollDue(int budget);

    /**
     * @return current pending row count
     */
    int size();
}
