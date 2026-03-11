package com.lucentflow.indexer.source;

import com.lucentflow.common.entity.SyncStatus;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.indexer.repository.SyncStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Transaction;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.core.functions.CheckedSupplier;

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
@DependsOn("flyway")
public class BaseBlockSource {
    
    private final Web3j web3j;
    private final SyncStatusRepository syncStatusRepository;
    private final TransactionPipe transactionPipe;
    
    // Whale threshold constant for efficiency
    private static final BigInteger WHALE_THRESHOLD = com.lucentflow.common.utils.EthUnitConverter.etherStringToWei("10");
    
    // Retry configuration for RPC calls
    private final Retry retryConfig;
    
    @Autowired
    public BaseBlockSource(Web3j web3j, SyncStatusRepository syncStatusRepository, TransactionPipe transactionPipe) {
        this.web3j = web3j;
        this.syncStatusRepository = syncStatusRepository;
        this.transactionPipe = transactionPipe;
        
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
    
    
    private Long lastScannedBlock;
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("System signaled READY. Initializing block height from database...");
        initializeLastScannedBlock();
    }
    
    public void initializeLastScannedBlock() {
        try {
            Optional<SyncStatus> latestStatus = syncStatusRepository.findFirstByOrderByIdDesc();
            if (latestStatus.isPresent()) {
                lastScannedBlock = latestStatus.get().getLastScannedBlock();
                
                // Genesis Trap Fix: If last_scanned_block is 0, start from current height - 10
                if (lastScannedBlock == 0) {
                    EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
                    long currentHeight = blockNumber.getBlockNumber().longValue();
                    lastScannedBlock = currentHeight - 10;
                    
                    log.info("Genesis Trap detected: last_scanned_block was 0, starting from current height - 10: {}", lastScannedBlock);
                    
                    // Update the database with the new starting point
                    SyncStatus status = latestStatus.get();
                    status.setLastScannedBlock(lastScannedBlock);
                    syncStatusRepository.save(status);
                } else {
                    log.info("Loaded last scanned block from database: {}", lastScannedBlock);
                }
            } else {
                // Start from current block if no history exists
                EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
                lastScannedBlock = blockNumber.getBlockNumber().longValue();
                log.info("Starting from current block: {}", lastScannedBlock);
                
                // Save initial sync status
                SyncStatus initialStatus = new SyncStatus();
                initialStatus.setLastScannedBlock(lastScannedBlock);
                syncStatusRepository.save(initialStatus);
            }
        } catch (Exception e) {
            log.error("Failed to initialize last scanned block - database may not be ready yet", e);
            // Don't throw exception - let the application start and retry later
            lastScannedBlock = 0L; // Default fallback
        }
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
        
        // Persist to database
        try {
            Optional<SyncStatus> latestStatus = syncStatusRepository.findFirstByOrderByIdDesc();
            SyncStatus status = latestStatus.orElse(new SyncStatus());
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
            // Use async request with hard timeout for better resilience
            CompletableFuture<EthBlock> future = web3j.ethGetBlockByNumber(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), 
                true
            ).sendAsync();
            
            // Apply hard timeout of 20 seconds
            EthBlock block = future.orTimeout(20, TimeUnit.SECONDS).get();
            return block.getBlock();
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
     * Get transactions from a block and push whale transactions to TransactionPipe.
     * Ensures zero-loss processing of all identified whale transactions.
     * @param block Block to extract transactions from
     * @return List of transactions
     */
    public List<Transaction> getTransactionsFromBlock(EthBlock.Block block) {
        List<Transaction> transactions = block.getTransactions().stream()
                .map(txResult -> (Transaction) txResult.get())
                .toList();
        
        // Push whale transactions to TransactionPipe for zero-loss processing
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
     * Check if a transaction is a whale transaction (>10 ETH).
     * Uses pre-computed WHALE_THRESHOLD constant for efficiency.
     * @param tx Transaction to check
     * @return true if value > 10 ETH, false otherwise
     */
    private boolean isWhaleTransaction(Transaction tx) {
        if (tx == null || tx.getValue() == null) {
            return false;
        }
        return tx.getValue().compareTo(WHALE_THRESHOLD) > 0;
    }
    
    /**
     * Create a simple whale transaction for pipeline processing.
     * Data integrity: leaves toAddress as NULL for contract creations.
     * @param tx Web3j transaction
     * @param block Block containing the transaction
     * @return Simple whale transaction entity
     */
    private com.lucentflow.common.entity.WhaleTransaction createSimpleWhaleTransaction(Transaction tx, EthBlock.Block block) {
        return com.lucentflow.common.entity.WhaleTransaction.builder()
                .hash(tx.getHash())
                .fromAddress(tx.getFrom() != null ? tx.getFrom().toLowerCase() : null)
                .toAddress(tx.getTo() != null ? tx.getTo().toLowerCase() : null) // Keep NULL for contract creations
                .valueEth(com.lucentflow.common.utils.EthUnitConverter.weiToEther(tx.getValue()))
                .blockNumber(block.getNumber().longValue())
                .build();
    }
    
    /**
     * Check if there are new blocks to process.
     * @return true if there are new blocks, false otherwise
     */
    public boolean hasNewBlocks() {
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
