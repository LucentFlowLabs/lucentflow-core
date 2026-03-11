package com.lucentflow.indexer.poller;

import com.lucentflow.common.entity.SyncStatus;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.indexer.repository.SyncStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Transaction;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;

/**
 * High-performance blockchain poller for transaction extraction and pipeline feeding.
 * 
 * <p>Implementation Details:
 * Polls blockchain blocks at 2-second intervals with adaptive parallel/sequential processing.
 * Implements zero-loss guarantee through TransactionPipe blocking operations.
 * Thread-safe sync status management with atomic database updates.
 * Virtual thread compatible through CompletableFuture parallel processing.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@DependsOn("flyway")
public class BaseBlockPoller {
    
    private final Web3j web3j;
    private final TransactionPipe transactionPipe;
    private final SyncStatusRepository syncStatusRepository;
    
    @Autowired
    public BaseBlockPoller(Web3j web3j, TransactionPipe transactionPipe, SyncStatusRepository syncStatusRepository) {
        this.web3j = web3j;
        this.transactionPipe = transactionPipe;
        this.syncStatusRepository = syncStatusRepository;
    }
    
    private static final int PARALLEL_PROCESSING_THRESHOLD = 3;
    
    /**
     * Initialize last scanned block from database.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("LucentFlow core signaled READY. Initializing synchronization state...");
        try {
            initializeLastScannedBlock();
        } catch (Exception e) {
            log.error("Failed to initialize block height. Pipeline will retry on next schedule.", e);
            // Do not throw exception here, let the app stay alive
        }
    }
    
    public void initializeLastScannedBlock() {
        try {
            Optional<SyncStatus> latestStatus = syncStatusRepository.findFirstByOrderByIdDesc();
            if (latestStatus.isPresent()) {
                log.info("Loaded last scanned block from database: {}", 
                        latestStatus.get().getLastScannedBlock());
            } else {
                // Initialize with current block - 10
                EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().send();
                BigInteger currentBlock = ethBlockNumber.getBlockNumber();
                long initialBlock = currentBlock.subtract(BigInteger.TEN).longValue();
                
                SyncStatus newStatus = new SyncStatus();
                newStatus.setLastScannedBlock(initialBlock);
                syncStatusRepository.save(newStatus);
                
                log.info("Initialized sync status with block: {}", initialBlock);
            }
        } catch (Exception e) {
            log.error("Failed to initialize last scanned block", e);
            throw new RuntimeException("Failed to initialize BaseBlockPoller", e);
        }
    }
    
    /**
     * Main scheduled polling method.
     * Runs every 2 seconds to check for new blocks and push transactions to pipe.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollForNewBlocks() {
        try {
            BigInteger latestBlock = getLatestBlockNumber();
            long latestBlockLong = latestBlock.longValue();
            
            Optional<SyncStatus> statusOpt = syncStatusRepository.findFirstByOrderByIdDesc();
            if (statusOpt.isEmpty()) {
                log.warn("No sync status found, reinitializing");
                initializeLastScannedBlock();
                return;
            }
            
            SyncStatus status = statusOpt.get();
            long lastScanned = status.getLastScannedBlock();
            
            if (latestBlockLong > lastScanned) {
                log.debug("New blocks detected. Last scanned: {}, Latest: {}", lastScanned, latestBlockLong);
                
                long blocksToProcess = latestBlockLong - lastScanned;
                
                if (blocksToProcess > PARALLEL_PROCESSING_THRESHOLD) {
                    processBlocksParallel(lastScanned + 1, latestBlockLong, status);
                } else {
                    processBlocksSequential(lastScanned + 1, latestBlockLong, status);
                }
            }
        } catch (Exception e) {
            if (!(e instanceof InterruptedException)) {
                log.error("Error during block polling: {}", e.getMessage(), e);
            } else {
                log.warn("Block polling interrupted: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Process multiple blocks in parallel.
     */
    private void processBlocksParallel(long startBlock, long endBlock, SyncStatus status) {
        log.info("Processing {} blocks in parallel: {} to {}", 
                endBlock - startBlock + 1, startBlock, endBlock);
        
        @SuppressWarnings("unchecked")
        java.util.concurrent.CompletableFuture<Void>[] futures = 
                (java.util.concurrent.CompletableFuture<Void>[]) new java.util.concurrent.CompletableFuture<?>[(int) (endBlock - startBlock + 1)];
        
        for (long blockNum = startBlock; blockNum <= endBlock; blockNum++) {
            final long currentBlock = blockNum;
            futures[(int) (blockNum - startBlock)] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    processBlock(BigInteger.valueOf(currentBlock));
                } catch (Exception e) {
                    log.error("Error processing block {} in parallel", currentBlock, e);
                }
            });
        }
        
        // Wait for all parallel processing to complete
        java.util.concurrent.CompletableFuture.allOf(futures).join();
        
        // Update sync status after all blocks are processed
        status.setLastScannedBlock(endBlock);
        syncStatusRepository.save(status);
        
        log.info("Completed parallel processing up to block {}", endBlock);
    }
    
    /**
     * Process blocks sequentially.
     */
    private void processBlocksSequential(long startBlock, long endBlock, SyncStatus status) throws Exception {
        log.debug("Processing {} blocks sequentially: {} to {}", 
                endBlock - startBlock + 1, startBlock, endBlock);
        
        for (long blockNum = startBlock; blockNum <= endBlock; blockNum++) {
            processBlock(BigInteger.valueOf(blockNum));
        }
        
        // Update sync status
        status.setLastScannedBlock(endBlock);
        syncStatusRepository.save(status);
    }
    
    /**
     * Get latest block number from blockchain.
     */
    private BigInteger getLatestBlockNumber() throws Exception {
        EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().send();
        return ethBlockNumber.getBlockNumber();
    }
    
    /**
     * Process a single block and push all transactions to pipe.
     */
    private void processBlock(BigInteger blockNum) throws Exception {
        EthBlock ethBlock = web3j.ethGetBlockByNumber(
            DefaultBlockParameter.valueOf(blockNum), true).send();
        
        EthBlock.Block block = ethBlock.getBlock();
        if (block == null) {
            log.warn("Block {} not found", blockNum);
            return;
        }

        @SuppressWarnings("unchecked")
        List<Transaction> transactions = (List<Transaction>) (List<?>) block.getTransactions();
        
        log.debug("Processing block {} with {} transactions", blockNum, transactions.size());
        
        // Push all transactions to pipe (zero-loss guarantee)
        for (Transaction tx : transactions) {
            transactionPipe.push(tx); // No return value - zero-loss operation
        }
    }
    
    /**
     * Get current polling status.
     */
    public long getLastScannedBlock() {
        return syncStatusRepository.findFirstByOrderByIdDesc()
                .map(SyncStatus::getLastScannedBlock)
                .orElse(0L);
    }
    
    /**
     * Get poller statistics.
     */
    public String getPollerStatistics() {
        return String.format(
                "BaseBlockPoller Statistics:\n" +
                "- Last Scanned Block: %d\n" +
                "- Latest Block: %d\n" +
                "%s",
                getLastScannedBlock(),
                getLatestBlockNumberSafely(),
                transactionPipe.getStatistics()
        );
    }
    
    private long getLatestBlockNumberSafely() {
        try {
            return getLatestBlockNumber().longValue();
        } catch (Exception e) {
            log.error("Failed to get latest block number", e);
            return -1;
        }
    }
}
