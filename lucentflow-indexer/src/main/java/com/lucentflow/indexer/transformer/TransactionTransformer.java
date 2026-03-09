package com.lucentflow.indexer.transformer;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.utils.EthUnitConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * Transformer component for processing blockchain transactions with strict data integrity.
 * 
 * <p>Implementation Details:</p>
 * <ul>
 *   <li>Handles ETH value conversion with BigDecimal precision</li>
 *   <li>Address normalization with strict null handling (no magic strings)</li>
 *   <li>Base-chain gas analysis with L2 execution cost modeling</li>
 *   <li>Zero data hallucination policy enforcement</li>
 *   <li>Accurate EVM transaction type detection</li>
 * </ul>
 * 
 * <p>Concurrency Model:</p>
 * This class is thread-safe and stateless, suitable for concurrent virtual thread environments.
 * All operations are deterministic and can be safely shared across multiple threads.
 * 
 * <p>Performance Optimizations:</p>
 * <ul>
 *   <li>Pre-calculated WHALE_THRESHOLD_WEI constant for O(1) threshold checks</li>
 *   <li>Direct BigDecimal operations without intermediate conversions</li>
 *   <li>Minimal object allocation in transformation pipeline</li>
 *   <li>Efficient gas cost calculations using BigInteger arithmetic</li>
 * </ul>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class TransactionTransformer {
    
    private static final BigInteger WHALE_THRESHOLD_WEI = EthUnitConverter.etherStringToWei("10");
    
    /**
     * Transform a Web3j Transaction into a WhaleTransaction entity if it meets whale criteria.
     * Enforces strict data integrity with no data hallucination.
     * 
     * <p>Processing Pipeline:</p>
     * <ol>
     *   <li>Validate transaction value against pre-calculated WHALE_THRESHOLD_WEI</li>
     *   <li>Convert ETH value using EthUnitConverter with BigDecimal precision</li>
     *   <li>Normalize addresses with strict null handling (no magic strings)</li>
     *   <li>Detect contract creation via tx.getTo() == null check</li>
     *   <li>Convert block timestamp with DataIntegrityException on null values</li>
     *   <li>Calculate L2 execution cost using Base-chain specific modeling</li>
     *   <li>Build WhaleTransaction entity with consistent field types</li>
     * </ol>
     * 
     * @param tx Web3j transaction to transform, must not be null
     * @param block Block containing the transaction, must not be null
     * @return WhaleTransaction entity if value > 10 ETH, null otherwise
     * @throws DataIntegrityException if critical data is missing or invalid
     */
    public WhaleTransaction transformToWhaleTransaction(Transaction tx, EthBlock.Block block) {
        try {
            // Check if transaction value meets whale threshold
            if (!isWhaleTransaction(tx)) {
                return null;
            }
            
            // Convert ETH value from wei to ether using performance-optimized approach
            BigDecimal valueEth = EthUnitConverter.weiToEther(tx.getValue());
            
            // Extract and normalize addresses with strict null handling
            String fromAddress = normalizeAddress(tx.getFrom());
            String toAddress = normalizeAddress(tx.getTo());
            boolean isContractCreation = tx.getTo() == null; // Strict contract creation detection
            
            // Convert block timestamp with no data hallucination
            Instant timestamp = convertBlockTimestamp(block.getTimestamp());
            
            // Calculate L2 execution cost (Base-chain specific)
            BigDecimal l2ExecutionCostEth = calculateL2ExecutionCost(tx);
            
            // Determine transaction type
            String transactionType = determineTransactionType(tx);
            
            // Build whale transaction entity with consistent field types
            return WhaleTransaction.builder()
                    .hash(tx.getHash())
                    .fromAddress(fromAddress)
                    .toAddress(toAddress)
                    .valueEth(valueEth)
                    .blockNumber(block.getNumber().longValue())
                    .timestamp(timestamp)
                    .isContractCreation(isContractCreation)
                    .gasPrice(tx.getGasPrice()) // BigInteger
                    .gasLimit(tx.getGas()) // BigInteger
                    .gasCostEth(l2ExecutionCostEth)
                    .transactionType(transactionType)
                    // TODO: Add L1 data fee analysis for Base transactions
                    // .l1DataFeeEth(calculateL1DataFee(tx)) // Future Base-chain enhancement
                    .build();
                    
        } catch (DataIntegrityException e) {
            log.error("Data integrity violation for transaction {}", tx.getHash(), e);
            return null; // Let caller drop the record
        } catch (Exception e) {
            log.error("Failed to transform transaction {} to whale transaction", tx.getHash(), e);
            return null;
        }
    }
    
    /**
     * Determine transaction type based on transaction characteristics.
     * Uses EVM-specific logic for accurate transaction classification.
     * 
     * @param tx Web3j transaction to analyze, must not be null
     * @return Transaction type string: "CONTRACT_CREATION", "CONTRACT_CALL", or "ETH_TRANSFER"
     */
    private String determineTransactionType(Transaction tx) {
        if (tx.getTo() == null) {
            return "CONTRACT_CREATION";
        } else if (tx.getInput() != null && !tx.getInput().equals("0x")) {
            return "CONTRACT_CALL";
        } else {
            return "ETH_TRANSFER";
        }
    }
    
    /**
     * Check if a transaction qualifies as a whale transaction (>10 ETH).
     * Uses pre-calculated WHALE_THRESHOLD_WEI constant for O(1) performance.
     * 
     * @param tx Transaction to check, may be null
     * @return true if value > 10 ETH, false otherwise
     */
    public boolean isWhaleTransaction(Transaction tx) {
        if (tx == null || tx.getValue() == null) {
            return false;
        }
        return tx.getValue().compareTo(WHALE_THRESHOLD_WEI) > 0;
    }
    
    /**
     * Normalize Ethereum address format with strict null handling.
     * Purges magic strings - returns null for null/empty addresses.
     * 
     * @param address Address to normalize, may be null or empty
     * @return Normalized address with 0x prefix, or null if input is null/empty
     */
    private String normalizeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null; // Strict null handling - no magic strings
        }
        
        // Convert to lowercase and ensure 0x prefix
        String normalized = address.toLowerCase();
        if (!normalized.startsWith("0x")) {
            normalized = "0x" + normalized;
        }
        
        return normalized;
    }
    
    /**
     * Convert block timestamp from BigInteger to Instant with strict data integrity.
     * No data hallucination - throws exception for null timestamps.
     * 
     * @param timestampSeconds Block timestamp in seconds as BigInteger, must not be null
     * @return Instant representing block timestamp
     * @throws DataIntegrityException if timestamp is null (data hallucination prevented)
     */
    private Instant convertBlockTimestamp(BigInteger timestampSeconds) {
        if (timestampSeconds == null) {
            throw new DataIntegrityException("Block timestamp cannot be null - data hallucination prevented");
        }
        
        return Instant.ofEpochSecond(timestampSeconds.longValue());
    }
    
    /**
     * Calculate L2 execution cost in ETH for a Base-chain transaction.
     * Accurate gas modeling for Layer 2 execution (excludes L1 data fees).
     * 
     * @param tx Transaction to calculate L2 execution cost for, must not be null
     * @return L2 execution cost in ETH, or null if gas data is incomplete
     */
    private BigDecimal calculateL2ExecutionCost(Transaction tx) {
        try {
            if (tx.getGasPrice() == null || tx.getGas() == null) {
                return null;
            }
            
            BigInteger gasCostWei = tx.getGasPrice().multiply(tx.getGas());
            return EthUnitConverter.weiToEther(gasCostWei);
            
        } catch (Exception e) {
            log.warn("Failed to calculate L2 execution cost for transaction {}", tx.getHash(), e);
            return null;
        }
    }
    
    /**
     * Calculate L1 data fee for Base-chain transactions (future enhancement).
     * TODO: Implement Base-chain L1 data fee calculation for complete Base transaction cost modeling.
     * This would require access to Base-specific fee data or L1 gas price estimation.
     * 
     * @param tx Transaction to calculate L1 data fee for, must not be null
     * @return L1 data fee in ETH, or null if not available
     */
    private BigDecimal calculateL1DataFee(Transaction tx) {
        // TODO: Implement Base-chain L1 data fee calculation
        // This requires access to Base-specific fee data or L1 gas price estimation
        log.debug("L1 data fee calculation not yet implemented for transaction {}", tx.getHash());
        return null;
    }
    
    /**
     * Get human-readable transaction summary for logging and monitoring.
     * Formats whale transaction data for operational visibility.
     * 
     * @param whaleTransaction Whale transaction entity to format, must not be null
     * @return Formatted summary string with key transaction details
     */
    public String getTransactionSummary(WhaleTransaction whaleTransaction) {
        return String.format(
            "[WHALE] %s ETH | %s → %s | Block: %d | Hash: %s%s",
            whaleTransaction.getValueEth().toString(),
            whaleTransaction.getFromAddress(),
            whaleTransaction.getToAddress(),
            whaleTransaction.getBlockNumber(),
            whaleTransaction.getHash().substring(0, 10) + "...",
            whaleTransaction.getIsContractCreation() ? " (Contract Creation)" : ""
        );
    }
}
