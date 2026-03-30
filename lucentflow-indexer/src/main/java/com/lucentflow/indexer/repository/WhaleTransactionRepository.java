package com.lucentflow.indexer.repository;

import com.lucentflow.common.entity.WhaleTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for high-performance whale transaction operations.
 * 
 * <p>Implementation Details:
 * Extends JpaRepository with optimized queries for whale transaction analytics.
 * Provides comprehensive search capabilities across multiple dimensions.
 * Thread-safe through Spring Data repository abstraction.
 * Virtual thread compatible through stateless query operations.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface WhaleTransactionRepository extends JpaRepository<WhaleTransaction, Long> {
    
    /**
     * Find whale transaction by hash (unique constraint)
     * @param hash Transaction hash
     * @return Optional containing the whale transaction if exists
     */
    Optional<WhaleTransaction> findByHash(String hash);

    /**
     * Count contract-creation whale transactions from the same initiator after a time boundary.
     * Optimized for serial deployer / factory heuristics (indexed {@code from_address} + {@code timestamp}, creations-only).
     *
     * @param fromAddress transaction initiator (deployer)
     * @param since       exclusive lower bound on {@link WhaleTransaction#getTimestamp()}
     * @return number of matching rows
     */
    @Query("SELECT COUNT(w) FROM WhaleTransaction w WHERE w.fromAddress = :fromAddress AND w.isContractCreation = true AND w.timestamp > :since")
    long countRecentDeployments(@Param("fromAddress") String fromAddress, @Param("since") Instant since);
    
    /**
     * Check if whale transaction exists by hash (optimized for batch operations)
     * @param hash Transaction hash
     * @return true if exists, false otherwise
     */
    boolean existsByHash(String hash);
    
    /**
     * Find whale transactions with value greater than or equal to minimum
     * @param minValue Minimum ETH value
     * @param pageable Pagination information
     * @return Page of whale transactions with value >= minValue
     */
    Page<WhaleTransaction> findByValueEthGreaterThanEqual(BigDecimal minValue, Pageable pageable);
    
    /**
     * Find whale transactions by from address
     * @param fromAddress Sender address
     * @param pageable Pagination information
     * @return Page of whale transactions from the specified address
     */
    Page<WhaleTransaction> findByFromAddress(String fromAddress, Pageable pageable);
    
    /**
     * Find whale transactions by to address
     * @param toAddress Receiver address
     * @param pageable Pagination information
     * @return Page of whale transactions to the specified address
     */
    Page<WhaleTransaction> findByToAddress(String toAddress, Pageable pageable);
    
    /**
     * Find whale transactions by block number
     * @param blockNumber Block number
     * @return List of whale transactions in the specified block
     */
    List<WhaleTransaction> findByBlockNumber(Long blockNumber);
    
    /**
     * Find whale transactions within a value range
     * @param minValue Minimum ETH value
     * @param maxValue Maximum ETH value
     * @param pageable Pagination information
     * @return Page of whale transactions within the value range
     */
    Page<WhaleTransaction> findByValueEthBetween(BigDecimal minValue, BigDecimal maxValue, Pageable pageable);
    
    /**
     * Find whale transactions within a time range
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param pageable Pagination information
     * @return Page of whale transactions within the time range
     */
    Page<WhaleTransaction> findByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * Find contract creation whale transactions
     * @param pageable Pagination information
     * @return Page of contract creation whale transactions
     */
    Page<WhaleTransaction> findByIsContractCreationTrue(Pageable pageable);
    
    /**
     * Find whale transactions with gas cost above threshold
     * @param minGasCost Minimum gas cost in ETH
     * @param pageable Pagination information
     * @return Page of whale transactions with high gas costs
     */
    @Query("SELECT wt FROM WhaleTransaction wt WHERE wt.gasCostEth > :minGasCost ORDER BY wt.gasCostEth DESC")
    Page<WhaleTransaction> findByGasCostEthGreaterThan(@Param("minGasCost") BigDecimal minGasCost, Pageable pageable);
    
    /**
     * Get whale transaction statistics for a time period
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return Array with [total_count, total_value_eth, avg_value_eth]
     */
    @Query("SELECT COUNT(wt), SUM(wt.valueEth), AVG(wt.valueEth) FROM WhaleTransaction wt WHERE wt.timestamp BETWEEN :startTime AND :endTime")
    Object[] getTransactionStatistics(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find top whale transactions by value in a time period
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param limit Maximum number of results
     * @return List of top whale transactions by value
     */
    @Query("SELECT wt FROM WhaleTransaction wt WHERE wt.timestamp BETWEEN :startTime AND :endTime ORDER BY wt.valueEth DESC")
    List<WhaleTransaction> findTopTransactionsByValue(@Param("startTime") LocalDateTime startTime, 
                                                     @Param("endTime") LocalDateTime endTime, 
                                                     Pageable pageable);
}
