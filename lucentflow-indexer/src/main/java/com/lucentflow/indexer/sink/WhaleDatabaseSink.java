package com.lucentflow.indexer.sink;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Semaphore;

/**
 * High-throughput database sink for whale transaction persistence.
 * 
 * <p>Implementation Details:
 * Provides async database operations with semaphore-controlled backpressure.
 * Implements duplicate detection and batch processing capabilities.
 * Thread-safe concurrent write limiting to prevent database pool exhaustion.
 * Virtual thread compatible through async execution and stateless operations.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class WhaleDatabaseSink {
    
    private final WhaleTransactionRepository whaleTransactionRepository;
    private final Semaphore dbSemaphore;
    
    @Autowired
    public WhaleDatabaseSink(WhaleTransactionRepository whaleTransactionRepository) {
        this.whaleTransactionRepository = whaleTransactionRepository;
        // Limit concurrent DB writes to max-pool-size - 2 (assuming pool size of 20)
        this.dbSemaphore = new Semaphore(18); // Conservative limit to prevent pool exhaustion
    }
    
    /**
     * Save a whale transaction to the database using async execution with throttling.
     * @param whaleTransaction Whale transaction to save
     */
    @Async("lucentTaskExecutor")
    @Transactional
    public void saveWhaleTransaction(WhaleTransaction whaleTransaction) {
        // Acquire semaphore to limit concurrent DB writes
        try {
            dbSemaphore.acquire();
            try {
                // Check if transaction already exists (avoid duplicates)
                if (whaleTransactionRepository.findByHash(whaleTransaction.getHash()).isPresent()) {
                    log.debug("Whale transaction {} already exists, skipping", whaleTransaction.getHash());
                    return;
                }
                
                // Save to database
                WhaleTransaction saved = whaleTransactionRepository.save(whaleTransaction);
                log.info("Saved whale transaction: {} ETH | {} → {} | Block: {}", 
                        saved.getValueEth(),
                        saved.getFromAddress(),
                        saved.getToAddress(),
                        saved.getBlockNumber());
                
            } finally {
                // Always release semaphore
                dbSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("DB write interrupted for transaction {}", whaleTransaction.getHash());
        } catch (Exception e) {
            log.error("Failed to save whale transaction {}", whaleTransaction.getHash(), e);
            throw new RuntimeException("Failed to save whale transaction", e);
        }
    }
    
    /**
     * Save multiple whale transactions in batch.
     * @param whaleTransactions List of whale transactions to save
     * @return Number of successfully saved transactions
     */
    @Async("lucentTaskExecutor")
    @Transactional
    public int saveWhaleTransactionsBatch(Iterable<WhaleTransaction> whaleTransactions) {
        int savedCount = 0;
        
        for (WhaleTransaction whaleTransaction : whaleTransactions) {
            try {
                // Check for duplicates
                if (!whaleTransactionRepository.findByHash(whaleTransaction.getHash()).isPresent()) {
                    WhaleTransaction saved = whaleTransactionRepository.save(whaleTransaction);
                    savedCount++;
                    log.debug("Saved whale transaction {} in batch", saved.getHash());
                }
            } catch (Exception e) {
                log.error("Failed to save whale transaction {} in batch", whaleTransaction.getHash(), e);
                // Continue with other transactions in batch
            }
        }
        
        if (savedCount > 0) {
            log.info("Saved {} whale transactions in batch", savedCount);
        }
        
        return savedCount;
    }
    
    /**
     * Check if a whale transaction already exists in the database.
     * @param transactionHash Transaction hash to check
     * @return true if exists, false otherwise
     */
    public boolean transactionExists(String transactionHash) {
        return whaleTransactionRepository.findByHash(transactionHash).isPresent();
    }
    
    /**
     * Get statistics about saved whale transactions.
     * @return Statistics string
     */
    public String getDatabaseStatistics() {
        try {
            long totalCount = whaleTransactionRepository.count();
            return String.format("Database contains %d whale transactions", totalCount);
        } catch (Exception e) {
            log.error("Failed to get database statistics", e);
            return "Database statistics unavailable";
        }
    }
}
