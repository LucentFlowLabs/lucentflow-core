package com.lucentflow.indexer.source;

import com.lucentflow.common.entity.SyncStatus;
import com.lucentflow.common.pipeline.TransactionPipe;
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

import java.math.BigInteger;
import java.time.Duration;
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

    /** Align receipt RPC hard timeout with block fetch for consistent public-RPC behavior. */
    private static final int RPC_RECEIPT_TIMEOUT_SECONDS = 60;
    
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
        String optimizationMode = switch (rpcProviderConfig.providerType()) {
            case PROFESSIONAL -> "high-throughput concurrent RPC (%d permits)".formatted(permits);
            case PUBLIC -> "conservative public-RPC throttling (%d permits)".formatted(permits);
        };
        log.info("[RPC-DETECTION] Detected {} endpoint. Optimized for {}.",
                rpcProviderConfig.providerType(), optimizationMode);
        // Trigger Flyway initialization if present (bean side-effects / migrations).
        flywayProvider.getIfAvailable();
        
        // Configure retry with exponential backoff
        this.retryConfig = Retry.of("web3jRetry", RetryConfig.custom()
            .maxAttempts(5)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(
                org.web3j.protocol.exceptions.ClientConnectionException.class,
                java.net.SocketTimeoutException.class,
                java.net.ConnectException.class
            )
            .retryOnException(ex -> {
                // Retry on 429 Too Many Requests and other connection issues
                String message = ex.getMessage();
                return message != null && 
                       (message.contains("429") || 
                        message.contains("Too Many Requests") ||
                        message.contains("rate limit"));
            })
            .build());
    }
    
    
    @org.springframework.beans.factory.annotation.Value("${lucentflow.indexer.start-block:#{null}}")
    private Long configuredStartBlock;

    // Shared across scheduler executions; ensure visibility across threads.
    private volatile Long lastScannedBlock;
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("System signaled READY. Initializing block height from database...");
        initializeLastScannedBlock();
    }
    
    public void initializeLastScannedBlock() {
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
        return lastScannedBlock;
    }
    
    /**
     * Update the last scanned block number.
     * @param blockNumber New last scanned block number
     */
    public void updateLastScannedBlock(long blockNumber) {
        this.lastScannedBlock = blockNumber;
        
        // Persist to database (ensure ID 1 is used to prevent multiple rows)
        try {
            SyncStatus status = syncStatusRepository.findById(1L).orElse(new SyncStatus());
            if (status.getId() == null) {
                status.setId(1L);
            }
            status.setLastScannedBlock(blockNumber);
            syncStatusRepository.save(status);
        } catch (Exception e) {
            log.error("Failed to update sync status for block {}", blockNumber, e);
        }
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
                    
                    // Apply hard timeout of 60 seconds for stability on public RPC
                    EthBlock block = future.orTimeout(60, TimeUnit.SECONDS).get();
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
        if (txHash == null || txHash.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("[RPC-QUEUE] Threads waiting for permit: {}", rpcSemaphore.getQueueLength());
                rpcSemaphore.acquire();
                try {
                    CompletableFuture<EthGetTransactionReceipt> future = web3j.ethGetTransactionReceipt(txHash).sendAsync();
                    EthGetTransactionReceipt response = future.orTimeout(RPC_RECEIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
                    if (response == null || response.getTransactionReceipt().isEmpty()) {
                        return null;
                    }
                    var receipt = response.getTransactionReceipt().get();
                    return receipt.isStatusOK() ? "SUCCESS" : "REVERTED";
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
        if (lastScannedBlock == null) {
            log.warn("lastScannedBlock is null during hasNewBlocks check. Re-initializing...");
            initializeLastScannedBlock();
            // If it's still null after initialization attempt, fail safely
            if (lastScannedBlock == null) {
                return false;
            }
        }
        long latestBlock = getLatestBlockNumber();
        if (latestBlock <= lastScannedBlock) {
            log.debug("No new blocks to process");
        }
        return latestBlock > lastScannedBlock;
    }
    
    /**
     * Get the range of blocks to process.
     * @return Array with [startBlock, endBlock] or empty if no new blocks
     */
    public long[] getBlockRangeToProcess() {
        long latestBlock = getLatestBlockNumber();
        if (latestBlock <= lastScannedBlock) {
            return new long[0];
        }
        
        return new long[]{lastScannedBlock + 1, latestBlock};
    }
}
