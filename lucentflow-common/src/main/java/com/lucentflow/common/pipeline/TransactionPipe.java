package com.lucentflow.common.pipeline;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, backpressure-aware transaction pipe for Java 21 virtual threads.
 *
 * <p><strong>In-process:</strong> {@link #push} blocks when full; live producers do not drop.
 * {@link #stopAccepting()} rejects further pushes (shutdown), which is not a live drop.</p>
 *
 * <p><strong>Crash:</strong> the pipe is not a WAL. After {@code sync_status} advances,
 * a kill before analyzer UPSERT is <strong>at-most-once</strong> for those hashes.
 * Do not report a 0% drop rate as crash-safe exactly-once.</p>
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class TransactionPipe {

    // T10 Standard: Always use bounded queues to prevent OOM
    private static final int QUEUE_CAPACITY = 5000;
    private final BlockingQueue<PipedTransaction> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong backpressureEvents = new AtomicLong(0);
    private final AtomicBoolean acceptPushes = new AtomicBoolean(true);

    /**
     * Pushes a transaction with built-in backpressure detection.
     * Prefer {@link #push(Transaction, Instant)} so catch-up ingest keeps the block time.
     */
    public void push(Transaction tx) throws InterruptedException {
        push(tx, Instant.now());
    }

    /**
     * Pushes a whale candidate together with the producing block timestamp.
     */
    public void push(Transaction tx, Instant blockTimestamp) throws InterruptedException {
        if (tx == null) {
            return;
        }
        if (!acceptPushes.get()) {
            log.debug("Rejecting push during pipe shutdown: {}", tx.getHash());
            return;
        }

        PipedTransaction item = new PipedTransaction(tx, blockTimestamp);
        int attempt = 0;
        while (!queue.offer(item, 1, TimeUnit.SECONDS)) {
            if (!acceptPushes.get()) {
                log.debug("Aborting push wait during pipe shutdown: {}", tx.getHash());
                return;
            }
            attempt++;
            backpressureEvents.incrementAndGet();
            if (attempt % 5 == 0) { // Log every 5 seconds of waiting
                log.warn("[STALL-ALERT] Producer waiting {}s for pipe space. Size: {}", attempt, queue.size());
            }
        }

        totalProcessed.incrementAndGet();
        log.debug("Tx {} added to pipe.", tx.getHash());
    }

    /**
     * Efficiently drains a batch of transactions for SQL bulk inserts.
     * This is the "Nuclear Engine" for consumer throughput.
     */
    public List<PipedTransaction> drainBatch(int batchSize) {
        List<PipedTransaction> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }

    /**
     * Drains every remaining item. Used by analyzer last-chance shutdown flush
     * so {@link #clear()} is not the persistence path.
     *
     * @return all queued items in FIFO order; empty when the pipe is already drained
     */
    public List<PipedTransaction> drainAll() {
        List<PipedTransaction> batch = new ArrayList<>(Math.max(1, queue.size()));
        queue.drainTo(batch);
        return batch;
    }

    /**
     * Returns the current size of the queue.
     *
     * @return Current number of transactions in the queue
     */
    public int size() {
        return queue.size();
    }

    public boolean hasPending() {
        return !queue.isEmpty();
    }

    /**
     * Stops accepting new pushes so consumers can drain remaining work.
     */
    public void stopAccepting() {
        acceptPushes.set(false);
        log.info("TransactionPipe stopped accepting pushes. Pending size={}", queue.size());
    }

    public String getStatistics() {
        double fillRate = (double) queue.size() / QUEUE_CAPACITY * 100;
        return String.format(
                "TransactionPipe Statistics (Backpressure-Aware):\n" +
                "- Current Size: %d / %d (%.1f%% full)\n" +
                "- Total Enqueued: %d\n" +
                "- Backpressure Events: %d\n" +
                "- In-process: blocking enqueue (no live drop)\n" +
                "- Crash: at-most-once after checkpoint (pipe is not WAL)",
                queue.size(), QUEUE_CAPACITY, fillRate,
                totalProcessed.get(),
                backpressureEvents.get()
        );
    }

    public long getBackpressureEvents() {
        return backpressureEvents.get();
    }

    @Scheduled(fixedDelay = 5000)
    public void logStatus() {
        if (queue.size() > (QUEUE_CAPACITY * 0.8)) {
            log.warn("[PIPE-ALERT] Queue is highly saturated: {}/{}", queue.size(), QUEUE_CAPACITY);
        } else {
            log.info("Pipe Status: {} tx buffered.", queue.size());
        }
    }

    /**
     * Bean destruction after {@code SmartLifecycle} stop. The analyzer must have
     * last-chance flushed; remaining items here are an ERROR (JVM is exiting).
     */
    @PreDestroy
    public void clear() {
        acceptPushes.set(false);
        int remaining = queue.size();
        if (remaining > 0) {
            log.error("TransactionPipe destroyed with {} unpersisted txs; analyzer last-chance flush missed them.",
                    remaining);
            queue.clear();
        } else {
            log.info("TransactionPipe empty at destroy; clearing complete.");
        }
        log.info("TransactionPipe destroy complete. Final stats: {} total processed, {} backpressure events.",
                totalProcessed.get(), backpressureEvents.get());
    }

    /**
     * Stops accepting pushes without discarding the queue (prefer consumer drain).
     */
    public void shutdown() {
        stopAccepting();
    }
}
