package com.lucentflow.indexer.source;

import com.lucentflow.common.entity.SyncStatus;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.common.utils.Erc20Decoder;
import com.lucentflow.indexer.repository.SyncStatusRepository;
import com.lucentflow.sdk.config.RpcProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.ObjectProvider;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.core.functions.CheckedSupplier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Source component for blockchain data extraction with zero-loss pipeline integration.
 * Handles polling logic for new blocks and transaction extraction.
 * Pushes all whale transactions to TransactionPipe for guaranteed processing.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class BaseBlockSource {
    
    private final Web3j web3j;
    private final SyncStatusRepository syncStatusRepository;
    private final TransactionPipe transactionPipe;

    /** Unified backpressure: permit count from {@link RpcProviderConfig} (professional vs public RPC). */
    private final Semaphore rpcSemaphore;
    
    // Virtual Thread Executor for non-blocking Async RPC calls
    private final ExecutorService rpcExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    // Whale threshold constant for efficiency
    private static final BigInteger WHALE_THRESHOLD = com.lucentflow.common.utils.EthUnitConverter.etherStringToWei("10");

    private final int rpcBlockTimeoutSeconds;
    private final int rpcReceiptTimeoutSeconds;
    
    // Retry configuration for RPC calls
    private final Retry retryConfig;
    
    @Autowired
    public BaseBlockSource(Web3j web3j,
                           SyncStatusRepository syncStatusRepository,
                           TransactionPipe transactionPipe,
                           RpcProviderConfig rpcProviderConfig,
                           ObjectProvider<org.flywaydb.core.Flyway> flywayProvider) {
        this.web3j = web3j;
        this.syncStatusRepository = syncStatusRepository;
        this.transactionPipe = transactionPipe;
        int permits = rpcProviderConfig.recommendedRpcSemaphorePermits();
        this.rpcSemaphore = new Semaphore(permits, true);
        // Timeout calibration: professional endpoints should fail fast and retry.
        int timeoutSeconds = switch (rpcProviderConfig.providerType()) {
            case PROFESSIONAL -> 10;
            case PUBLIC -> 60;
        };
        this.rpcBlockTimeoutSeconds = timeoutSeconds;
        this.rpcReceiptTimeoutSeconds = timeoutSeconds;
        String optimizationMode = switch (rpcProviderConfig.providerType()) {
            case PROFESSIONAL -> "high-throughput concurrent RPC (%d permits)".formatted(permits);
            case PUBLIC -> "conservative public-RPC throttling (%d permits)".formatted(permits);
        };
        log.info("[RPC-DETECTION] Detected {} endpoint. Optimized for {}.",
                rpcProviderConfig.providerType(), optimizationMode);
        if (rpcProviderConfig.providerType() == com.lucentflow.sdk.config.RpcProviderType.PROFESSIONAL) {
            // Diagnostic for tuning: used to detect whether 429 bursts return.
            log.info("[PERF-STEALTH] Running in high-stability mode. {} permits, {} chunk size.",
                    permits, rpcProviderConfig.recommendedPipelineChunkSize());
        }
        // Trigger Flyway initialization if present (bean side-effects / migrations).
        flywayProvider.getIfAvailable();
        
        // Configure retry with exponential backoff + jitter.
        // 429 gets an extra 10s sleep before retry starts to wait out rate-limit reset.
        // Note: do not combine waitDuration() with intervalFunction() — Resilience4j treats both as interval config and throws IllegalStateException.
        this.retryConfig = Retry.of("web3jRetry", RetryConfig.custom()
            .maxAttempts(5)
            .retryExceptions(
                org.web3j.protocol.exceptions.ClientConnectionException.class,
                java.net.SocketTimeoutException.class,
                java.net.ConnectException.class
            )
            .intervalFunction(
                // Exponential backoff with randomization (jitter) to avoid synchronized retries.
                io.github.resilience4j.core.IntervalFunction.ofExponentialRandomBackoff(5_000L, 2.0, 0.2, 60_000L)
            )
            .retryOnException(ex -> {
                String message = ex.getMessage();
                boolean is429 = message != null && message.toLowerCase().contains("429");
                boolean isRateLimit = message != null && (
                        message.toLowerCase().contains("too many requests") ||
                        message.toLowerCase().contains("rate limit")
                );

                // If it's a rate-limit hit, sleep extra before retry starts.
                if (is429 || isRateLimit) {
                    try {
                        Thread.sleep(10_000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                // retryExceptions already restrict which exception types are eligible.
                return true;
            })
            .build());
    }
    
    
    @org.springframework.beans.factory.annotation.Value("${lucentflow.indexer.start-block:#{null}}")
    private Long configuredStartBlock;

    // Shared across scheduler executions; ensure visibility across threads.
    private volatile Long lastScannedBlock;

    private final Object lastScannedBlockInitLock = new Object();
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Warm-up: trigger thread-safe lazy initialization.
        getLastScannedBlock();
    }
    
    public void initializeLastScannedBlock() {
        synchronized (lastScannedBlockInitLock) {
            try {
                long startBlockToProcess = resolveStartBlock();
                long resolvedLastScanned = Math.max(0L, startBlockToProcess - 1);

                this.lastScannedBlock = resolvedLastScanned;
                updateLastScannedBlock(resolvedLastScanned);

                log.info("Resolved start block: startBlockToProcess={} => last_scanned_block={}",
                        startBlockToProcess, resolvedLastScanned);
            } catch (Exception e) {
                log.error("Failed to initialize last scanned block - database or RPC may not be ready yet", e);
                // Don't throw exception - let the application start and retry later
                lastScannedBlock = 0L; // Default fallback
            }
        }
    }

    /**
     * Resolve the inclusive start block to process, using the ID 1 protocol.
     *
     * <ol>
     *   <li>If DB has progress in row id=1 and last_scanned_block > 0, return lastScannedBlock + 1.</li>
     *   <li>Else if configured start block is present (via .env / properties), return it.</li>
     *   <li>Else return latest - 10.</li>
     * </ol>
     *
     * <p>Note: migration seeds `last_scanned_block=0` as a sentinel. Treat 0 as "no progress".</p>
     *
     * @return inclusive block number to start processing
     */
    private long resolveStartBlock() {
        // 1) Prefer DB checkpoint row id=1
        try {
            Optional<SyncStatus> statusOpt = syncStatusRepository.findById(1L);
            if (statusOpt.isPresent()) {
                Long lastScanned = statusOpt.get().getLastScannedBlock();
                if (lastScanned != null && lastScanned > 0) {
                    return lastScanned + 1;
                }
                // Genesis Trap/Sentinel Fix: last_scanned_block=0 means "uninitialized"; do not resume from block 1.
                if (lastScanned != null && lastScanned == 0L) {
                    log.info("ID=1 checkpoint is sentinel (last_scanned_block=0). Falling back to configured start block / latest-10.");
                }
            } else {
                log.info("ID=1 checkpoint missing. Falling back to configured start block / latest-10.");
            }
        } catch (Exception e) {
            log.warn("Failed to read ID=1 checkpoint progress. Falling back to configured start block / latest-10.", e);
        }

        // 2) Fallback to configured start block (.env / properties)
        if (configuredStartBlock != null) {
            log.info("Using explicitly configured start block from properties/.env: {}", configuredStartBlock);
            return configuredStartBlock;
        }

        // 3) Fallback to latest - 10
        long latestBlock = getLatestBlockNumber();
        long fallbackStart = latestBlock - 10;
        return Math.max(0L, fallbackStart);
    }
    
    /**
     * Get the latest block number from the blockchain with retry logic.
     * @return Latest block number
     */
    public long getLatestBlockNumber() {
        CheckedSupplier<Long> blockNumberSupplier = Retry.decorateCheckedSupplier(retryConfig, () -> {
            EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
            return blockNumber.getBlockNumber().longValue();
        });
        
        try {
            return blockNumberSupplier.get();
        } catch (Throwable e) {
            log.error("Failed to get latest block number after 5 retries", e);
            throw new RuntimeException("Failed to fetch latest block", e);
        }
    }
    
    /**
     * Get the last scanned block number.
     * @return Last scanned block number
     */
    public long getLastScannedBlock() {
        Long v = lastScannedBlock;
        if (v != null) {
            return v;
        }
        // Double-checked locking: avoid WARN/temporary null access during startup.
        synchronized (lastScannedBlockInitLock) {
            v = lastScannedBlock;
            if (v == null) {
                initializeLastScannedBlock();
                v = lastScannedBlock;
            }
        }
        return v != null ? v : 0L;
    }
    
    /**
     * Update the last scanned block number.
     * @param blockNumber New last scanned block number
     */
    public void updateLastScannedBlock(long blockNumber) {
        this.lastScannedBlock = blockNumber;
        syncStatusRepository.upsertProgress(1L, blockNumber);
    }
    
    /**
     * Fetch a specific block from the blockchain with retry logic and hard timeout.
     * CRITICAL: Null return means missing potential whale movements - must be logged appropriately.
     * @param blockNumber Block number to fetch
     * @return Block data, or null if timeout occurs (logged as CRITICAL)
     */
    public EthBlock.Block fetchBlock(long blockNumber) {
        CheckedSupplier<EthBlock.Block> blockSupplier = Retry.decorateCheckedSupplier(retryConfig, () -> {
            try {
                // Apply strict backpressure using Semaphore
                log.debug("[RPC-QUEUE] Threads waiting for permit: {}", rpcSemaphore.getQueueLength());
                rpcSemaphore.acquire();
                try {
                    // Execute the blocking RPC call on a virtual thread to prevent ForkJoinPool starvation
                    CompletableFuture<EthBlock> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            return web3j.ethGetBlockByNumber(
                                DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), 
                                true
                            ).send();
                        } catch (Exception e) {
                            throw new RuntimeException("RPC call failed", e);
                        }
                    }, rpcExecutor);
                    
                    // Apply hard timeout based on provider tier
                    EthBlock block = future.orTimeout(rpcBlockTimeoutSeconds, TimeUnit.SECONDS).get();
                    if (block == null || block.getBlock() == null) {
                        throw new RuntimeException("Empty block returned from RPC");
                    }
                    return block.getBlock();
                } finally {
                    rpcSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[SOFT-INTERRUPT] Block {} fetch interrupted; returning null for orchestrator soft-fail", blockNumber);
                return null;
            } catch (java.util.concurrent.RejectedExecutionException e) {
                log.warn("Shutdown in progress - rejected task for block {}: {}", blockNumber, e.getMessage());
                return null;
            } catch (Exception e) {
                throw e; // Let Resilience4j handle the retry
            }
        });
        
        try {
            return blockSupplier.get();
        } catch (Throwable e) {
            log.error("Failed to fetch block {} after 5 retries", blockNumber, e);
            // CRITICAL: Null return means we're skipping a block - potential whale movements lost
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                log.error("CRITICAL: Block {} fetch timed out - SKIPPING BLOCK, POTENTIAL WHALE MOVEMENTS LOST", blockNumber);
                return null;
            }
            throw new RuntimeException("Failed to fetch block " + blockNumber, e);
        }
    }

    /**
     * Fetches transaction receipt execution status with unified backpressure control.
     *
     * <p>This method is guarded by the same {@code rpcSemaphore} used for block fetches to ensure
     * the public RPC node is not overwhelmed. Fair semaphore ordering (FIFO) prevents starvation
     * across block and receipt requests.</p>
     *
     * @param txHash transaction hash
     * @return CompletableFuture resolving to "SUCCESS", "REVERTED" or null when unavailable
     */
    public CompletableFuture<String> fetchExecutionStatusAsync(String txHash) {
        return fetchTransactionReceiptAsync(txHash).thenApply(opt -> opt
                .map(r -> r.isStatusOK() ? "SUCCESS" : "REVERTED")
                .orElse(null));
    }

    /**
     * Fetches the full transaction receipt (logs + status) under the same RPC semaphore as block fetches.
     * Used by Module 1 (execution status) and Module 3 (ERC-20 log decoding) with a single RPC round-trip.
     *
     * @param txHash transaction hash
     * @return receipt when available
     */
    public CompletableFuture<Optional<TransactionReceipt>> fetchTransactionReceiptAsync(String txHash) {
        if (txHash == null || txHash.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("[RPC-QUEUE] Threads waiting for permit: {}", rpcSemaphore.getQueueLength());
                rpcSemaphore.acquire();
                try {
                    CompletableFuture<EthGetTransactionReceipt> future = web3j.ethGetTransactionReceipt(txHash).sendAsync();
                    EthGetTransactionReceipt response = future.orTimeout(rpcReceiptTimeoutSeconds, TimeUnit.SECONDS).join();
                    if (response == null || response.getTransactionReceipt().isEmpty()) {
                        return Optional.<TransactionReceipt>empty();
                    }
                    return Optional.of(response.getTransactionReceipt().get());
                } finally {
                    rpcSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while fetching receipt for " + txHash, e);
            }
        }, rpcExecutor);
    }

    /**
     * Runs a blocking RPC call under the shared fair {@code rpcSemaphore}.
     * Use for any Web3j traffic that must participate in unified backpressure with
     * block and receipt fetches (prevents permit leaks via {@code finally} release).
     *
     * @param action RPC work to execute while holding a permit
     * @param <T>    result type
     * @return action result
     */
    public <T> T runWithRpcPermit(Callable<T> action) {
        try {
            log.debug("[RPC-QUEUE] Threads waiting for permit: {}", rpcSemaphore.getQueueLength());
            rpcSemaphore.acquire();
            try {
                return action.call();
            } finally {
                rpcSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for RPC permit", e);
        } catch (Exception e) {
            throw new RuntimeException("RPC call failed under permit", e);
        }
    }
    
    /**
     * Get transactions from a block and push whale transactions to TransactionPipe.
     * Ensures zero-loss processing of all identified whale transactions.
     * @param block Block to extract transactions from
     * @return List of transactions
     */
    public List<Transaction> getTransactionsFromBlock(EthBlock.Block block) {
        List<Transaction> transactions = block.getTransactions().stream()
                .map(txResult -> (Transaction) txResult.get())
                .toList();
        
        // Push whale-sized transfers and contract deployments to TransactionPipe (downstream applies finer rules)
        for (Transaction tx : transactions) {
            if (isWhaleTransaction(tx)) {
                try {
                    // Push the original Web3j Transaction to pipe (not WhaleTransaction)
                    transactionPipe.push(tx);
                    log.debug("Pushed whale transaction to pipe: {}", tx.getHash());
                } catch (InterruptedException e) {
                    log.error("Interrupt detected while pushing transaction {} to pipe. Restoring interrupt status...", tx.getHash());
                    Thread.currentThread().interrupt(); // T10 Standard: Must restore interrupt status
                    // Break the loop to avoid further processing when interrupted
                    break;
                }
            }
        }
        
        return transactions;
    }
    
    /**
     * Indexer ingress filter: pass high-value transfers or contract creations to the pipe.
     * Contract deployments typically have value 0; they must not be dropped here or rug analysis starves.
     *
     * @param tx Transaction to check
     * @return true if value &gt; 10 ETH or contract creation ({@code to} absent)
     */
    private boolean isWhaleTransaction(Transaction tx) {
        if (tx == null) {
            return false;
        }
        String to = tx.getTo();
        boolean isContractCreation = to == null || to.trim().isEmpty();
        if (isContractCreation) {
            return true;
        }
        // Module 3: candidate ERC-20 interactions with tracked Base core tokens (decoded from receipt downstream).
        if (Erc20Decoder.isCoreTokenContract(to)) {
            return true;
        }
        if (tx.getValue() == null) {
            return false;
        }
        return tx.getValue().compareTo(WHALE_THRESHOLD) > 0;
    }
    
    /**
     * Check if there are new blocks to process.
     * @return true if there are new blocks, false otherwise
     */
    public boolean hasNewBlocks() {
        long last = getLastScannedBlock();
        long latestBlock = getLatestBlockNumber();
        if (latestBlock <= last) {
            log.debug("No new blocks to process");
        }
        return latestBlock > last;
    }
    
    /**
     * Get the range of blocks to process.
     * @return Array with [startBlock, endBlock] or empty if no new blocks
     */
    public long[] getBlockRangeToProcess() {
        long latestBlock = getLatestBlockNumber();
        long last = getLastScannedBlock();
        if (latestBlock <= last) {
            return new long[0];
        }
        
        return new long[]{last + 1, latestBlock};
    }
}
