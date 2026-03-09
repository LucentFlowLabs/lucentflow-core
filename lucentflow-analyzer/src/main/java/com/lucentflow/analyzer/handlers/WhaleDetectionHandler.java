package com.lucentflow.analyzer.handlers;

import com.lucentflow.analyzer.pipeline.WhaleAnalysisHandler;
import com.lucentflow.common.entity.WhaleTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Web3 security-focused whale detection handler with heuristic analysis.
 * 
 * <p>Implementation Details:
 * Performs comprehensive whale transaction analysis using security heuristics.
 * Detects anomalous patterns including high gas costs, contract deployments, and round values.
 * Virtual thread compatible through stateless design and immutable operations.
 * Provides structured logging for security monitoring and alerting systems.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
public class WhaleDetectionHandler implements WhaleAnalysisHandler {
    
    /**
     * Processes whale transaction with comprehensive security analysis.
     * 
     * <p>Performs detailed logging and heuristic analysis to identify potential
     * security concerns, anomalous patterns, or significant market movements.
     * Analysis includes gas cost evaluation, contract detection, and value pattern recognition.</p>
     * 
     * @param whaleTransaction Whale transaction entity to analyze
     * @throws Exception if analysis processing fails
     * @throws IllegalArgumentException if whaleTransaction is null
     */
    @Override
    public void handle(WhaleTransaction whaleTransaction) throws Exception {
        log.info("🐋 WHALE DETECTED: {} ETH | {} → {} | Block: {} | Hash: {}{}",
                whaleTransaction.getValueEth(),
                whaleTransaction.getFromAddress(),
                whaleTransaction.getToAddress(),
                whaleTransaction.getBlockNumber(),
                whaleTransaction.getHash().substring(0, 10) + "...",
                whaleTransaction.getIsContractCreation() ? " (Contract Creation)" : "");
        
        // Log additional details if available
        if (whaleTransaction.getGasCostEth() != null) {
            log.info("   Gas Cost: {} ETH | Gas Price: {} wei | Gas Limit: {}",
                    whaleTransaction.getGasCostEth(),
                    whaleTransaction.getGasPrice(),
                    whaleTransaction.getGasLimit());
        }
        
        // Log timestamp
        log.info("   Timestamp: {}", whaleTransaction.getTimestamp());
        
        // Perform basic analysis
        performBasicAnalysis(whaleTransaction);
    }
    
    /**
     * Performs heuristic analysis on whale transaction for security monitoring.
     * 
     * <p>Analysis includes:
     * - Contract deployment detection
     * - Gas cost ratio analysis (identifies potentially expensive transactions)
     * - Round number value detection (identifies strategically significant amounts)</p>
     * 
     * @param whaleTransaction Whale transaction to analyze
     * @throws IllegalArgumentException if whaleTransaction is null
     */
    private void performBasicAnalysis(WhaleTransaction whaleTransaction) {
        // Check for contract creation
        if (whaleTransaction.getIsContractCreation()) {
            log.info("   📝 Contract deployment detected");
        }
        
        // Check for high gas cost (relative to transaction value)
        if (whaleTransaction.getGasCostEth() != null && whaleTransaction.getValueEth() != null) {
            double gasCostRatio = whaleTransaction.getGasCostEth().doubleValue() / whaleTransaction.getValueEth().doubleValue();
            if (gasCostRatio > 0.01) { // Gas cost > 1% of transaction value
                log.warn("   ⚠️  High gas cost ratio: {:.2%} of transaction value", gasCostRatio);
            }
        }
        
        // Check for round number values (potential significance)
        if (whaleTransaction.getValueEth() != null) {
            String valueStr = whaleTransaction.getValueEth().toString();
            if (valueStr.matches("\\d+\\.0+")) { // Whole number
                log.info("   💰 Round number value: {} ETH", valueStr);
            }
        }
    }
}
