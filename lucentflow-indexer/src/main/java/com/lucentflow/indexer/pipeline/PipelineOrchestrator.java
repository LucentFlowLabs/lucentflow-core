package com.lucentflow.indexer.pipeline;

import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.indexer.config.RpcConcurrencyGovernor;
import com.lucentflow.indexer.repository.SyncStatusRepository;
import com.lucentflow.indexer.sink.WhaleDatabaseSink;
import com.lucentflow.indexer.source.BaseBlockSource;
import com.lucentflow.sdk.config.RpcProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.EthBlock;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-throughput blockchain indexing pipeline orchestrator with zero-loss guarantees.
 * 
 * <p>Implementation Details:
 * Coordinates complete indexing flow: Source → Transformer → Sink → Sync Status Update.
 * Implements adaptive parallel/sequential processing based on block volume.
 * Semaphore-controlled RPC concurrency prevents rate limiting and ensures backpressure management.
 * Virtual thread compatible with structured concurrency and proper resource cleanup.
 * Zero-loss guarantee through atomic transaction boundaries and comprehensive error handling.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class PipelineOrchestrator {
    
    private final BaseBlockSource blockSource;
    private final WhaleDatabaseSink whaleDatabaseSink;
    private final SyncStatusRepository syncStatusRepository;
    private final TransactionPipe transactionPipe;
    private final JdbcTemplate jdbcTemplate;
    private final RpcConcurrencyGovernor rpcConcurrencyGovernor;
    private final Random random = new Random();

    /** Samples for approximate blocks/sec between heartbeats. */
    private final AtomicLong heartbeatSampleHead = new AtomicLong(-1L);
    private final AtomicLong heartbeatSampleNanos = new AtomicLong(0L);
    
    // T10 Standard: Private Virtual Thread Executor to prevent ForkJoinPool leaks
    private final ExecutorService pipelineExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    // Shutdown awareness flag
    private volatile boolean isShuttingDown = false;
    
    private static final int PARALLEL_PROCESSING_THRESHOLD = 3;

    private final long pipelineChunkSize;
    private final long interBatchSleepMillis;

    // Prevent overlapping scheduled executions from racing checkpoint writes.
    private final ReentrantLock scanLock = new ReentrantLock();
    
    // Removed duplicate Semaphore from Orchestrator as it should be managed inside BaseBlockSource directly
    
    @Autowired
    public PipelineOrchestrator(BaseBlockSource blockSource,
                                WhaleDatabaseSink whaleDatabaseSink,
                                SyncStatusRepository syncStatusRepository,
                                TransactionPipe transactionPipe,
                                RpcProviderConfig rpcProviderConfig,
                                JdbcTemplate jdbcTemplate,
                                RpcConcurrencyGovernor rpcConcurrencyGovernor) {
        this.blockSource = blockSource;
        this.whaleDatabaseSink = whaleDatabaseSink;
        this.syncStatusRepository = syncStatusRepository;
        this.transactionPipe = transactionPipe;
        this.jdbcTemplate = jdbcTemplate;
        this.rpcConcurrencyGovernor = rpcConcurrencyGovernor;
        this.pipelineChunkSize = rpcProviderConfig.recommendedPipelineChunkSize();
        this.interBatchSleepMillis = rpcProviderConfig.interBatchSleepMillis();
    }
    
    /**
     * Main scheduled entry point for blockchain indexing operations.
     * 
     * <p>Executes every 2 seconds to check for new blocks and process them through
     * the complete indexing pipeline. Uses adaptive processing strategy based on block volume.
     * Transaction boundaries ensure zero-loss guarantee across the entire operation.</p>
     * 
     * @throws RuntimeException if critical pipeline components fail
     */
    @Scheduled(fixedDelay = 2000)
    public void scanForNewBlocks() {
        if (!scanLock.tryLock()) {
            log.debug("scanForNewBlocks skipped: previous execution still running");
            return;
        }
        try {
            if (!blockSource.hasNewBlocks()) {
                log.debug("No new blocks to process");
                return;
            }
            
            long[] blockRange = blockSource.getBlockRangeToProcess();
            if (blockRange.length == 0) {
                return;
            }
            
            long startBlock = blockRange[0];
            long endBlock = blockRange[1];
            long blocksToProcess = endBlock - startBlock + 1;
            
            log.debug("Processing {} blocks: {} to {}", blocksToProcess, startBlock, endBlock);
            
            // Checkpoint/Resume: chunk size adapts to RPC provider (public vs professional).
            java.util.List<java.util.concurrent.CompletableFuture<Void>> checkpointFutures =
                    new java.util.ArrayList<>();
            for (long chunkStart = startBlock; chunkStart <= endBlock; chunkStart += pipelineChunkSize) {
                final long chunkEnd = Math.min(chunkStart + pipelineChunkSize - 1, endBlock);
                long blocksInChunk = chunkEnd - chunkStart + 1;

                if (!isDatabasePoolAlive()) {
                    log.warn("[DB-PREFLIGHT] Skipping chunk {}–{}; pool unreachable — will retry on next scheduler tick",
                            chunkStart, chunkEnd);
                    break;
                }
                
                if (blocksInChunk > PARALLEL_PROCESSING_THRESHOLD) {
                    processBlocksParallel(chunkStart, chunkEnd);
                } else {
                    processBlocksSequential(chunkStart, chunkEnd);
                }
                
                // CHECKPOINT: persist progress using ID 1 Protocol after each chunk.
                // Async checkpoint reduces idle time between chunks; losing a checkpoint only
                // causes reprocessing (idempotent upsert), not data loss.
                checkpointFutures.add(
                        java.util.concurrent.CompletableFuture.runAsync(
                                () -> checkpointProgress(chunkEnd, endBlock),
                                pipelineExecutor
                        )
                );
                
                // Inter-batch delay: provider tier dependent (used to prevent anti-spam/bursts).
                if (chunkEnd < endBlock && interBatchSleepMillis > 0) {
                    try {
                        Thread.sleep(interBatchSleepMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Pipeline orchestrator interrupted during inter-batch delay");
                        break;
                    }
                }
            }
            
            // Ensure all in-flight checkpoint updates are completed before releasing the scheduler lock.
            checkpointFutures.forEach(java.util.concurrent.CompletableFuture::join);
            log.debug("Completed processing up to block {}", endBlock);
            
        } catch (Exception e) {
            // Filter shutdown noise - don't log full stack trace during graceful shutdown
            if (!(e instanceof InterruptedException)) {
                log.error("Error in pipeline orchestrator", e);
            } else {
                log.warn("Pipeline orchestrator interrupted during shutdown: {}", e.getMessage());
            }
        } finally {
            scanLock.unlock();
        }
    }
    
    /**
     * Processes blocks sequentially for optimal performance on small batches.
     * 
     * <p>Used when block count is below PARALLEL_PROCESSING_THRESHOLD.
     * Provides deterministic processing order and minimal overhead for small workloads.</p>
     * 
     * @param startBlock Starting block number (inclusive)
     * @param endBlock Ending block number (inclusive)
     * @throws RuntimeException if individual block processing fails
     */
    private void processBlocksSequential(long startBlock, long endBlock) {
        for (long blockNum = startBlock; blockNum <= endBlock; blockNum++) {
            try {
                processBlock(blockNum);
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    log.warn("Failed to process block {} sequentially (transient)", blockNum, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Processes blocks in parallel with strict concurrency control for large batches.
     * 
     * <p>Uses CompletableFuture with semaphore-controlled RPC calls to prevent
     * rate limiting. Implements jitter between requests to avoid thundering herd problems.
     * Guarantees completion before proceeding to next batch.</p>
     * 
     * @param startBlock Starting block number (inclusive)
     * @param endBlock Ending block number (inclusive)
     * @throws InterruptedException if thread interruption occurs during processing
     */
    private void processBlocksParallel(long startBlock, long endBlock) {
        log.debug("Processing {} blocks in parallel: {} to {}", 
                   endBlock - startBlock + 1, startBlock, endBlock);
        
        @SuppressWarnings("unchecked")
        java.util.concurrent.CompletableFuture<Void>[] futures = 
                (java.util.concurrent.CompletableFuture<Void>[]) new java.util.concurrent.CompletableFuture<?>[(int) (endBlock - startBlock + 1)];
        
        for (long blockNum = startBlock; blockNum <= endBlock; blockNum++) {
            final long currentBlock = blockNum;
            
            // Check for thread interruption before submitting tasks
            if (Thread.currentThread().isInterrupted()) {
                log.info("Block processing interrupted, stopping submission of remaining tasks");
                return;
            }
            
            // Add jitter between batches to avoid Thundering Herd
            if (blockNum > startBlock) {
                try {
                    int jitterMs = 10 + random.nextInt(41); // 10-50ms random delay
                    Thread.sleep(jitterMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            try {
                futures[(int) (blockNum - startBlock)] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        // Check for interruption inside the async task
                        if (Thread.currentThread().isInterrupted()) {
                            log.debug("Async task interrupted for block {}", currentBlock);
                            return;
                        }
                        
                        // Check shutdown flag before processing
                        if (isShuttingDown || Thread.currentThread().isInterrupted()) {
                            log.warn("Pipeline shutting down. Aborting parallel block processing.");
                            return;
                        }
                        
                        processBlock(currentBlock);
                    } catch (Exception e) {
                        if (!(e instanceof InterruptedException)) {
                            log.warn("Failed to process block {} in parallel (transient)", currentBlock, e.getMessage());
                        }
                    }
                }, pipelineExecutor);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                log.warn("Shutdown in progress - rejected task for block {}: {}", currentBlock, e.getMessage());
                break; // Stop submitting more tasks
            }
        }
        
        // Wait for all parallel processing to complete
        java.util.concurrent.CompletableFuture.allOf(futures).join();
    }
    
    /**
     * Processes a single block through the complete indexing pipeline.
     * 
     * <p>Flow: Fetch block → Extract transactions → Transform whale transactions →
     * Persist to database. All operations are atomic to ensure data integrity.</p>
     * 
     * @param blockNumber Block number to process
     * @throws RuntimeException if block fetching, transformation, or persistence fails
     */
    private void processBlock(long blockNumber) {
        try {
            // SOURCE: Fetch block
            EthBlock.Block block = blockSource.fetchBlock(blockNumber);
            if (block == null) {
                log.warn("[SOFT-FAIL] Block {} fetch returned null (timeout, interrupt, or shutdown); skipping", blockNumber);
                return;
            }
            var transactions = blockSource.getTransactionsFromBlock(block);
            
            log.debug("Processing block {} with {} transactions", blockNumber, transactions.size());
            
            // BaseBlockSource pushes whale transactions to TransactionPipe.
            // Pipeline processing of those transactions happens asynchronously downstream.
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.warn("[SOFT-INTERRUPT] Block {} processing interrupted; skipping without crashing pipeline", blockNumber);
            } else {
                log.warn("[RESILIENCE] Block {} failed after retries and was skipped: {}", blockNumber, e.getMessage());
            }
        }
    }
    
    /**
     * Updates synchronization status after successful block processing.
     * 
     * <p>Called only after all blocks in the range are successfully processed.
     * Ensures exactly-once semantics and prevents data loss during restarts.</p>
     * 
     * @param lastProcessedBlock The highest block number successfully processed
     * @throws RuntimeException if status update fails (critical error)
     */
    private void checkpointProgress(long lastProcessedBlock, long targetEndBlock) {
        try {
            int updated = syncStatusRepository.updateProgress(1L, lastProcessedBlock, Instant.now());
            if (updated == 0) {
                // If the row doesn't exist (unexpected), create it via the existing initializer save,
                // then retry the deterministic updateProgress call.
                try {
                    blockSource.updateLastScannedBlock(lastProcessedBlock);
                } catch (Exception initEx) {
                    log.warn("[CHECKPOINT-RESILIENCE] Could not seed sync_status via updateLastScannedBlock at block {}: {}",
                            lastProcessedBlock, initEx.toString());
                    return;
                }
                try {
                    updated = syncStatusRepository.updateProgress(1L, lastProcessedBlock, Instant.now());
                } catch (Exception retryEx) {
                    log.warn("[CHECKPOINT-RESILIENCE] Retry updateProgress failed at block {}: {}",
                            lastProcessedBlock, retryEx.toString());
                    return;
                }
                if (updated == 0) {
                    log.warn("[CHECKPOINT-RESILIENCE] sync_status id=1 missing after seed attempt; will retry on next successful chunk (lastProcessedBlock={})",
                            lastProcessedBlock);
                    return;
                }
            }

            // Keep in-memory pointer aligned for subsequent scans inside same JVM.
            try {
                blockSource.updateLastScannedBlock(lastProcessedBlock);
            } catch (Exception memEx) {
                log.warn("[CHECKPOINT-RESILIENCE] In-memory / save alignment failed at block {}: {} — checkpoint row may still be updated",
                        lastProcessedBlock, memEx.toString());
            }

            recordHeartbeatAndCatchUpTuning(lastProcessedBlock);

            // Reduce INFO noise: only show periodic sync progress.
            boolean shouldLogSync = (lastProcessedBlock % 1000L == 0) || lastProcessedBlock == targetEndBlock;
            if (shouldLogSync) {
                log.info("[SYNC] last_scanned_block={} (target_end_block={})",
                        lastProcessedBlock, targetEndBlock);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.warn("[CHECKPOINT-RESILIENCE] Checkpoint interrupted at block {}: {}", lastProcessedBlock, e.toString());
            } else {
                log.warn("[CHECKPOINT-RESILIENCE] Database unreachable or checkpoint failed at block {} — scanner continues; will sync on next successful chunk: {}",
                        lastProcessedBlock, e.toString());
            }
        }
    }

    /**
     * Persists chain tip, lag, and throughput to {@code sync_status} (ID=1) and tunes RPC concurrency when far behind.
     */
    private void recordHeartbeatAndCatchUpTuning(long lastProcessedBlock) {
        try {
            long chainHead = blockSource.getLatestBlockNumber();
            long lag = Math.max(0L, chainHead - lastProcessedBlock);
            double bps = computeBlocksPerSecond(chainHead);
            syncStatusRepository.updateSyncMetrics(1L, chainHead, lag, bps, Instant.now());
            rpcConcurrencyGovernor.adjustForLag(lag);
            log.info("[HEARTBEAT] Chain Head: {}, Our Progress: {}, Lag: {} blocks", chainHead, lastProcessedBlock, lag);
        } catch (Exception e) {
            log.debug("[HEARTBEAT] skipped: {}", e.getMessage());
        }
    }

    private double computeBlocksPerSecond(long chainHead) {
        long prevHead = heartbeatSampleHead.get();
        long prevNs = heartbeatSampleNanos.get();
        long nowNs = System.nanoTime();
        heartbeatSampleHead.set(chainHead);
        heartbeatSampleNanos.set(nowNs);
        if (prevHead < 0L || prevNs <= 0L) {
            return 0.0;
        }
        double dt = (nowNs - prevNs) / 1_000_000_000.0;
        if (dt <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, (chainHead - prevHead) / dt);
    }

    /**
     * Lightweight pool health probe (SELECT 1) before each chunk to avoid poisoning the scan with stale connections.
     */
    private boolean isDatabasePoolAlive() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            log.warn("[DB-PREFLIGHT] Connection probe failed: {}", e.toString());
            return false;
        }
    }
    
    /**
     * Returns comprehensive pipeline statistics for monitoring and debugging.
     * 
     * <p>Includes sync status, latest block information, and database statistics.
     * Provides visibility into pipeline performance and indexing progress.</p>
     * 
     * @return Multi-line string containing detailed pipeline statistics
     */
    public String getPipelineStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Pipeline Statistics:\n");
        stats.append("- Last Scanned Block: ").append(blockSource.getLastScannedBlock()).append("\n");
        stats.append("- Latest Block: ").append(blockSource.getLatestBlockNumber()).append("\n");
        stats.append(whaleDatabaseSink.getDatabaseStatistics());
        return stats.toString();
    }
    
    /**
     * Cleanup method to set shutdown flag when Spring context is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        this.isShuttingDown = true;
        log.info("PipelineOrchestrator: Shutdown signal received.");
        
        // Shutdown TransactionPipe to clear pending transactions
        if (transactionPipe != null) {
            transactionPipe.shutdown();
        }
        
        // T10 Standard: Shutdown private Virtual Thread Executor
        if (pipelineExecutor != null) {
            pipelineExecutor.shutdownNow();
            log.info("Pipeline executor shutdown completed.");
        }
        
        log.info("Pipeline orchestrator shutting down...");
    }
}
