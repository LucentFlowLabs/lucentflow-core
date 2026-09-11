package com.lucentflow.common.webhook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Process-local dead-letter used by unit tests. Production uses {@link JdbcWebhookDeadLetterStore}.
 *
 * @author ArchLucent
 * @since 1.2
 */
public final class InMemoryWebhookDeadLetterStore implements WebhookDeadLetterStore {

    private final BlockingQueue<WebhookDeadLetterRecord> queue;

    /**
     * @param capacity max pending rows (minimum 1)
     */
    public InMemoryWebhookDeadLetterStore(int capacity) {
        this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
    }

    @Override
    public boolean enqueue(WebhookDeadLetterRecord record) {
        if (record == null) {
            return false;
        }
        return queue.offer(record);
    }

    @Override
    public List<WebhookDeadLetterRecord> pollDue(int budget) {
        List<WebhookDeadLetterRecord> batch = new ArrayList<>(Math.max(0, budget));
        if (budget <= 0) {
            return batch;
        }
        queue.drainTo(batch, budget);
        return batch;
    }

    @Override
    public int size() {
        return queue.size();
    }
}
