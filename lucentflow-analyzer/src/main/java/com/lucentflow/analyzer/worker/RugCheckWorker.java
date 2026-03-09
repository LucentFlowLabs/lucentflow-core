package com.lucentflow.analyzer.worker;

import com.lucentflow.common.utils.EthUnitConverter;
import com.lucentflow.common.pipeline.TransactionPipe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Web3 security worker for suspicious transaction and rug pull detection.
 * 
 * <p>Implementation Details:
 * Demonstrates plug-and-play extensibility through TransactionPipe consumer pattern.
 * Implements heuristic analysis for large transactions and suspicious patterns.
 * Thread-safe atomic counters for statistics tracking and monitoring.
 * Virtual thread compatible through stateless transaction analysis design.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RugCheckWorker implements TransactionPipe.TransactionConsumer {
    
    private final TransactionPipe transactionPipe;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong suspiciousCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    // Suspicious transaction threshold: 100 ETH
    private static final BigInteger SUSPICIOUS_THRESHOLD_WEI = EthUnitConverter.etherStringToWei("100");
    
    /**
     * Start long-running rug check worker thread.
     */
    @PostConstruct
    public void startWorker() {
        log.info("Starting RugCheckWorker for suspicious transaction detection");
        
        Thread workerThread = new Thread(() -> {
            log.info("RugCheckWorker started and ready to analyze transactions");
            
            while (true) {
                try {
                    // Blocking call - waits for transaction from pipe
                    Transaction tx = transactionPipe.take();
                    handle(tx);
                } catch (InterruptedException e) {
                    log.info("RugCheckWorker interrupted, shutting down");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Unexpected error in RugCheckWorker", e);
                    errorCount.incrementAndGet();
                }
            }
        });
        
        workerThread.setName("rug-check-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        
        log.info("RugCheckWorker thread started: {}", workerThread.getName());
    }
    
    /**
     * Handle a transaction from the pipe for rug check analysis.
     * @param tx Transaction to handle
     */
    @Override
    public void handle(Transaction tx) {
        processedCount.incrementAndGet();
        
        try {
            // Perform rug check analysis
            boolean isSuspicious = analyzeForRugPull(tx);
            
            if (isSuspicious) {
                suspiciousCount.incrementAndGet();
                
                log.warn("🚨 SUSPICIOUS TRANSACTION: {} ETH | {} → {} | Hash: {}",
                        EthUnitConverter.weiToEther(tx.getValue()),
                        tx.getFrom() != null ? tx.getFrom() : "UNKNOWN",
                        tx.getTo() != null ? tx.getTo() : "CONTRACT_CREATION",
                        tx.getHash());
                
                // In real implementation, this would trigger alerts, save to suspicious DB, etc.
                // For now, just log the detection
            }
            
            // Log progress every 500 transactions (less frequent than whale worker)
            if (processedCount.get() % 500 == 0) {
                log.info("RugCheck Progress: {} processed, {} suspicious, {} errors", 
                        processedCount.get(), suspiciousCount.get(), errorCount.get());
            }
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Error analyzing transaction {} in RugCheckWorker", tx.getHash(), e);
        }
    }
    
    /**
     * Analyze transaction for potential rug pull patterns.
     * @param tx Transaction to analyze
     * @return true if suspicious, false otherwise
     */
    private boolean analyzeForRugPull(Transaction tx) {
        // Check 1: Very large transactions (potential liquidity removal)
        if (tx.getValue() != null && tx.getValue().compareTo(SUSPICIOUS_THRESHOLD_WEI) > 0) {
            return true;
        }
        
        // Check 2: Contract creation with large value (potential honeypot)
        if (tx.getTo() == null && tx.getValue() != null && 
            tx.getValue().compareTo(EthUnitConverter.etherStringToWei("50")) > 0) {
            return true;
        }
        
        // Check 3: Transactions to known suspicious addresses (placeholder logic)
        if (tx.getTo() != null && isSuspiciousAddress(tx.getTo())) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if address is known to be suspicious.
     * In real implementation, this would query a database or API.
     */
    private boolean isSuspiciousAddress(String address) {
        // Placeholder logic - in production, this would check against a database
        // of known suspicious addresses, blacklists, etc.
        return address != null && (
                address.startsWith("0xdead") || 
                address.startsWith("0xbeef") ||
                address.startsWith("0xcafe")
        );
    }
    
    /**
     * Get rug check worker statistics.
     */
    public String getWorkerStatistics() {
        double suspiciousRate = processedCount.get() > 0 ? 
                (double) suspiciousCount.get() / processedCount.get() * 100 : 0.0;
        
        return String.format(
                "RugCheckWorker Statistics:\n" +
                "- Total Processed: %d\n" +
                "- Suspicious Transactions: %d (%.2f%%)\n" +
                "- Error Count: %d\n" +
                "- Suspicious Rate: 1 per %d transactions",
                processedCount.get(),
                suspiciousCount.get(),
                suspiciousRate,
                errorCount.get(),
                suspiciousCount.get() > 0 ? processedCount.get() / suspiciousCount.get() : 0
        );
    }
}
