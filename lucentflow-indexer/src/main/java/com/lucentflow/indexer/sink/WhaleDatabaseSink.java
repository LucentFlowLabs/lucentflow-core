package com.lucentflow.indexer.sink;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * High-throughput PostgreSQL batch sink for whale transaction persistence.
 * 
 * <p>Implementation Details:
 * Optimized for PostgreSQL reWriteBatchedInserts=true with true batch operations.
 * Virtual thread compatible through stateless operations and simplified concurrency.
 * Leverages Hibernate batch_size=50 for maximum database throughput.
 * </p>
 * 
 * @author High-Performance Database Architect
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleDatabaseSink {
    
    private final WhaleTransactionRepository whaleTransactionRepository;
    
    /**
     * Save a whale transaction to the database (legacy method for compatibility).
     * @param whaleTransaction Whale transaction to save
     */
    @Transactional
    public void saveWhaleTransaction(WhaleTransaction whaleTransaction) {
        if (whaleTransaction == null) return;
        
        try {
            // Check if transaction already exists (avoid duplicates)
            if (whaleTransactionRepository.existsByHash(whaleTransaction.getHash())) {
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
                    
        } catch (Exception e) {
            log.error("Failed to save whale transaction {}", whaleTransaction.getHash(), e);
            throw new RuntimeException("Failed to save whale transaction", e);
        }
    }
    
    /**
     * Save multiple whale transactions in true PostgreSQL batch.
     * Leverages Hibernate batch_size=50 and reWriteBatchedInserts=true for maximum throughput.
     * @param transactions List of whale transactions to save
     */
    @Transactional
    public void saveWhaleTransactions(List<WhaleTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return;

        try {
            // T10 Performance: saveAll() is required for Hibernate batching to work
            whaleTransactionRepository.saveAll(transactions);
            log.info("[SINK] Successfully persisted batch of {} transactions.", transactions.size());
        } catch (Exception e) {
            log.error("[SINK] Batch save failed. Falling back to individual persistence for error isolation...", e);
            // Fallback to save one-by-one only on failure to identify the specific record causing issues
            for (WhaleTransaction tx : transactions) {
                try {
                    if (!whaleTransactionRepository.existsByHash(tx.getHash())) {
                        whaleTransactionRepository.save(tx);
                    }
                } catch (Exception ex) {
                    log.error("[SINK] Individual save failed for hash {}: {}", tx.getHash(), ex.getMessage());
                }
            }
        }
    }
    
    /**
     * Check if a whale transaction already exists in the database.
     * @param transactionHash Transaction hash to check
     * @return true if exists, false otherwise
     */
    public boolean transactionExists(String transactionHash) {
        return whaleTransactionRepository.existsByHash(transactionHash);
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
