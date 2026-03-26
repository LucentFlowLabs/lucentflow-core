package com.lucentflow.indexer.sink;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * High-throughput PostgreSQL batch sink for whale transaction persistence.
 * 
 * <p>Implementation Details:
 * Uses Native SQL UPSERT with JdbcTemplate for robust duplicate handling without exception poisoning.
 * Optimized for PostgreSQL reWriteBatchedInserts=true with true batch operations.
 * Virtual thread compatible through stateless operations and simplified concurrency.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleDatabaseSink {
    
    private final WhaleTransactionRepository whaleTransactionRepository;
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Save a whale transaction to the database (legacy method for compatibility).
     * @param whaleTransaction Whale transaction to save
     */
    @Transactional
    public void saveWhaleTransaction(WhaleTransaction whaleTransaction) {
        if (whaleTransaction == null) return;
        saveWhaleTransactions(List.of(whaleTransaction));
    }
    
    /**
     * Save multiple whale transactions in true PostgreSQL batch using Native UPSERT.
     * Prevents duplicate constraint violations and ensures idempotency.
     * @param transactions List of whale transactions to save
     */
    @Transactional
    public void saveWhaleTransactions(List<WhaleTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return;

        String sql = """
            INSERT INTO whale_transactions (
                hash, from_address, to_address, value_eth, block_number, 
                timestamp, is_contract_creation, gas_price, gas_limit, gas_cost_eth, 
                transaction_type, from_address_tag, to_address_tag, whale_category, 
                address_tag, transaction_category, funding_source_address, funding_source_tag, 
                rug_risk_level, risk_score, risk_reasons, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (hash) DO NOTHING
            """;

        try {
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    WhaleTransaction tx = transactions.get(i);
                    ps.setString(1, tx.getHash());
                    ps.setString(2, tx.getFromAddress());
                    ps.setString(3, tx.getToAddress());
                    ps.setBigDecimal(4, tx.getValueEth());
                    ps.setLong(5, tx.getBlockNumber());
                    
                    // Handle timestamps carefully
                    java.time.Instant ts = tx.getTimestamp() != null ? tx.getTimestamp() : java.time.Instant.now();
                    ps.setTimestamp(6, Timestamp.from(ts));
                    
                    ps.setBoolean(7, Boolean.TRUE.equals(tx.getIsContractCreation()));
                    
                    // BigIntegers to BigDecimal/Numeric
                    ps.setBigDecimal(8, tx.getGasPrice() != null ? new java.math.BigDecimal(tx.getGasPrice()) : null);
                    ps.setBigDecimal(9, tx.getGasLimit() != null ? new java.math.BigDecimal(tx.getGasLimit()) : null);
                    ps.setBigDecimal(10, tx.getGasCostEth());
                    
                    ps.setString(11, tx.getTransactionType() != null ? tx.getTransactionType() : "UNKNOWN");
                    ps.setString(12, tx.getFromAddressTag());
                    ps.setString(13, tx.getToAddressTag());
                    ps.setString(14, tx.getWhaleCategory());
                    ps.setString(15, tx.getAddressTag());
                    ps.setString(16, tx.getTransactionCategory());
                    ps.setString(17, tx.getFundingSourceAddress());
                    ps.setString(18, tx.getFundingSourceTag());
                    ps.setString(19, tx.getRugRiskLevel());
                    
                    if (tx.getRiskScore() != null) {
                        ps.setInt(20, tx.getRiskScore());
                    } else {
                        ps.setNull(20, java.sql.Types.INTEGER);
                    }
                    ps.setString(21, tx.getRiskReasons());
                    
                    java.time.Instant now = java.time.Instant.now();
                    ps.setTimestamp(22, Timestamp.from(tx.getCreatedAt() != null ? tx.getCreatedAt() : now));
                    ps.setTimestamp(23, Timestamp.from(tx.getUpdatedAt() != null ? tx.getUpdatedAt() : now));
                }

                @Override
                public int getBatchSize() {
                    return transactions.size();
                }
            });
            log.info("[SINK] Successfully processed batch of {} transactions via UPSERT.", transactions.size());
        } catch (Exception e) {
            log.error("[SINK] Native batch UPSERT failed.", e);
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
