package com.lucentflow.common.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, Backpressure-aware Transaction Pipe.
 * Designed for Java 21 Virtual Threads and high-throughput L2 monitoring.
 */
@Slf4j
@Component
public class TransactionPipe {
    
    // T10 Standard: Always use bounded queues to prevent OOM
    private static final int QUEUE_CAPACITY = 2048;
    private final BlockingQueue<Transaction> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong backpressureEvents = new AtomicLong(0);

    /**
     * Pushes a transaction with built-in backpressure detection.
     */
    public void push(Transaction tx) throws InterruptedException {
        if (tx == null) return;

        // Try to offer with a 5s timeout to detect if consumers are stalling
        boolean accepted = queue.offer(tx, 5, TimeUnit.SECONDS);
        
        if (!accepted) {
            backpressureEvents.incrementAndGet();
            log.warn("[BACKPRESSURE] Pipe is FULL (size: {}). Producer is now stalling...", queue.size());
            // Strict blocking put: No data loss, but producer slows down to consumer's speed
            queue.put(tx); 
        }
        
        totalProcessed.incrementAndGet();
        log.debug("Tx {} added to pipe.", tx.getHash());
    }

    /**
     * Efficiently drains a batch of transactions for SQL bulk inserts.
     * This is the "Nuclear Engine" for consumer throughput.
     */
    public List<Transaction> drainBatch(int batchSize) {
        List<Transaction> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }

    /**
     * Returns the current size of the queue.
     * @return Current number of transactions in the queue
     */
    public int size() {
        return queue.size();
    }

    /**
     * Statistics for Metabase/Log monitoring.
     */
    public String getStatistics() {
        double fillRate = (double) queue.size() / QUEUE_CAPACITY * 100;
        return String.format(
                "TransactionPipe Statistics (Backpressure-Aware):\n" +
                "- Current Size: %d / %d (%.1f%% full)\n" +
                "- Total Processed: %d\n" +
                "- Backpressure Events: %d\n" +
                "- Drop Rate: 0.00%% (zero-loss guarantee)",
                queue.size(), QUEUE_CAPACITY, fillRate,
                totalProcessed.get(),
                backpressureEvents.get()
        );
    }

    @Scheduled(fixedDelay = 5000)
    public void logStatus() {
        if (queue.size() > (QUEUE_CAPACITY * 0.8)) {
            log.warn("[PIPE-ALERT] Queue is highly saturated: {}/{}", queue.size(), QUEUE_CAPACITY);
        } else {
            log.info("Pipe Status: {} tx buffered.", queue.size());
        }
    }
}