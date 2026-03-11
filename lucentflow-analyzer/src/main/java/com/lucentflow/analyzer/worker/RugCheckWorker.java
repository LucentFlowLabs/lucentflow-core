package com.lucentflow.analyzer.worker;

import com.lucentflow.common.utils.EthUnitConverter;
import com.lucentflow.common.pipeline.TransactionPipe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Web3 security worker for suspicious transaction and rug pull detection.
 * 
 * <p>Implementation Details:
 * High-performance batch-processing worker using Java 21 Virtual Threads.
 * Implements heuristic analysis for large transactions and suspicious patterns.
 * Leverages TransactionPipe.drainBatch() for maximum throughput.
 * Stateless transaction analysis ensures virtual thread compatibility.
 * </p>
 * 
 * @author T10 Java & Web3 Architect
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RugCheckWorker implements CommandLineRunner {
    
    private final TransactionPipe transactionPipe;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong suspiciousCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    // Suspicious transaction threshold: 100 ETH
    private static final BigInteger SUSPICIOUS_THRESHOLD_WEI = EthUnitConverter.etherStringToWei("100");
    private static final int BATCH_SIZE = 50;

    @Override
    public void run(String... args) {
        log.info("Initializing RugCheckWorker with Virtual Thread for batch processing.");
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        List<Transaction> batch = transactionPipe.drainBatch(BATCH_SIZE);
                        
                        if (batch.isEmpty()) {
                            // Efficient waiting: pause for 100ms if no data
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                            continue;
                        }

                        // Process batch in memory
                        batch.forEach(this::analyzeForRugRisk);
                        processedCount.addAndGet(batch.size());
                        
                        if (processedCount.get() % 500 < BATCH_SIZE) {
                            log.info("[RUG-CHECK] Analysis throughput: {} processed, {} suspicious detected.", 
                                    processedCount.get(), suspiciousCount.get());
                        }

                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        log.error("Critical error in RugCheckWorker: {}", e.getMessage());
                    }
                }
            });
            
            // Keep the main thread alive for the executor's lifetime
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RugCheckWorker main thread interrupted.");
        }
    }

    /**
     * Analyze transaction for potential rug pull patterns.
     * @param tx Transaction to analyze
     */
    private void analyzeForRugRisk(Transaction tx) {
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
