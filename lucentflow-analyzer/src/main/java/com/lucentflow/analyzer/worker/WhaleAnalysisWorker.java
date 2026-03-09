package com.lucentflow.analyzer.worker;

import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.indexer.sink.WhaleDatabaseSink;
import com.lucentflow.common.constant.BaseChainConstants;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.utils.EthUnitConverter;
import com.lucentflow.common.pipeline.TransactionPipe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance whale transaction analysis worker with virtual thread support.
 * 
 * <p>Implementation Details:
 * Processes transactions from TransactionPipe with Web3 security analysis.
 * Implements Java 21 virtual threads with graceful fallback to traditional threads.
 * Thread-safe atomic counters for comprehensive statistics tracking.
 * Stateless transaction analysis ensures virtual thread compatibility.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleAnalysisWorker implements TransactionPipe.TransactionConsumer {
    
    private final TransactionPipe transactionPipe;
    private final AddressLabeler addressLabeler;
    private final WhaleDatabaseSink whaleDatabaseSink;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong whaleCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    /**
     * Start long-running virtual thread worker using Java 21 Project Loom with reflection fallback.
     */
    @PostConstruct
    public void startWorker() {
        log.info("Starting WhaleAnalysisWorker on virtual thread");
        
        try {
            // Try Java 21+ virtual thread approach
            Object threadBuilder = Thread.class.getMethod("ofVirtual").invoke(null);
            Object namedBuilder = threadBuilder.getClass().getMethod("name", String.class, int.class)
                    .invoke(threadBuilder, "whale-analysis-", 0);
            namedBuilder.getClass().getMethod("start", Runnable.class)
                    .invoke(namedBuilder, (Runnable) () -> {
                        log.info("WhaleAnalysisWorker started on virtual thread and ready to process transactions");
                        
                        while (true) {
                            try {
                                // Take transaction from pipe (blocking call)
                                Transaction tx = transactionPipe.take();
                                
                                // Process the transaction
                                handle(tx);
                                
                            } catch (InterruptedException e) {
                                log.info("WhaleAnalysisWorker interrupted, shutting down gracefully");
                                break;
                            } catch (Exception e) {
                                log.error("Unexpected error in WhaleAnalysisWorker", e);
                                // Continue processing after error
                            }
                        }
                    });
            log.info("WhaleAnalysisWorker started successfully on virtual thread");
        } catch (Exception e) {
            // Fallback to traditional thread for Java < 21
            log.warn("Virtual threads not available, falling back to traditional thread", e);
            Thread workerThread = new Thread(() -> {
                log.info("WhaleAnalysisWorker started on traditional thread and ready to process transactions");
                
                while (true) {
                    try {
                        // Take transaction from pipe (blocking call)
                        Transaction tx = transactionPipe.take();
                        
                        // Process the transaction
                        handle(tx);
                        
                    } catch (InterruptedException e2) {
                        log.info("WhaleAnalysisWorker interrupted, shutting down gracefully");
                        break;
                    } catch (Exception e2) {
                        log.error("Unexpected error in WhaleAnalysisWorker", e2);
                        // Continue processing after error
                    }
                }
            });
            workerThread.start();
            log.info("WhaleAnalysisWorker started successfully on traditional thread");
        }
    }
    
    /**
     * Handle a transaction from the pipe with database persistence.
     * @param tx Transaction to handle
     */
    @Override
    public void handle(Transaction tx) {
        processedCount.incrementAndGet();
        
        try {
            // Check if this is a whale transaction
            if (isWhaleTransaction(tx)) {
                whaleCount.incrementAndGet();
                
                // Create enhanced whale transaction
                WhaleTransaction whaleTx = createWhaleTransaction(tx);
                
                if (whaleTx != null) {
                    // Enrich with Web3 analysis
                    enrichWithWeb3Analysis(whaleTx, tx);
                    
                    // ONLY place where whaleDatabaseSink.save() is called
                    whaleDatabaseSink.saveWhaleTransaction(whaleTx);
                    
                    log.info("[ANALYSIS] {} detected! Value: {} ETH | From: {} -> To: {} | Hash: {}",
                            whaleTx.getTransactionCategory(),
                            whaleTx.getValueEth().toPlainString(),
                            whaleTx.getAddressTag(),
                            addressLabeler.getAddressLabel(tx.getTo()),
                            whaleTx.getHash());
                }
            }
            
            // Log progress every 1000 transactions
            if (processedCount.get() % 1000 == 0) {
                log.info("Worker Progress: {} processed, {} whales, {} errors", 
                        processedCount.get(), whaleCount.get(), errorCount.get());
            }
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Error processing transaction {} in worker", tx.getHash(), e);
        }
    }
    
    /**
     * Create whale transaction from Web3j transaction.
     * @param tx Web3j transaction
     * @return Whale transaction entity
     */
    private WhaleTransaction createWhaleTransaction(Transaction tx) {
        try {
            WhaleTransaction whaleTx = new WhaleTransaction();
            whaleTx.setHash(tx.getHash());
            whaleTx.setFromAddress(tx.getFrom());
            whaleTx.setToAddress(tx.getTo());
            whaleTx.setValueEth(EthUnitConverter.weiToEther(tx.getValue()));
            whaleTx.setBlockNumber(tx.getBlockNumber().longValue());
            whaleTx.setGasPrice(tx.getGasPrice());
            whaleTx.setGasLimit(BigInteger.valueOf(21000)); // Default gas limit for simple transfers
            whaleTx.setIsContractCreation(tx.getTo() == null);
            
            return whaleTx;
        } catch (Exception e) {
            log.error("Error creating whale transaction from {}", tx.getHash(), e);
            return null;
        }
    }
    
    /**
     * Enrich whale transaction with Web3 native analysis using BigDecimal precision.
     * @param whaleTx Whale transaction to enrich
     * @param tx Original Web3j transaction
     */
    private void enrichWithWeb3Analysis(WhaleTransaction whaleTx, Transaction tx) {
        // Get address labels
        String fromLabel = addressLabeler.getAddressLabel(tx.getFrom());
        String toLabel = addressLabeler.getAddressLabel(tx.getTo());
        
        // Set primary address tag (from address for most analysis)
        whaleTx.setAddressTag(fromLabel);
        
        // Get transaction category using AddressLabeler with BigDecimal precision
        BigDecimal valueEth = whaleTx.getValueEth();
        String transactionCategory = addressLabeler.getTransactionCategory(
                tx.getFrom(), tx.getTo(), valueEth);
        whaleTx.setTransactionCategory(transactionCategory);
        
        // Set whale size category using BigDecimal precision
        String whaleCategory = BaseChainConstants.classifyWhaleSize(valueEth);
        whaleTx.setWhaleCategory(whaleCategory);
        
        // Set transaction type
        String transactionType = determineTransactionType(tx);
        whaleTx.setTransactionType(transactionType);
        
        // Set address tags
        whaleTx.setFromAddressTag(fromLabel);
        whaleTx.setToAddressTag(toLabel);
    }
    
    /**
     * Determine transaction type based on transaction characteristics.
     * @param tx Web3j transaction
     * @return Transaction type
     */
    private String determineTransactionType(Transaction tx) {
        if (tx.getTo() == null) {
            return BaseChainConstants.CONTRACT_DEPLOYMENT;
        } else if (BaseChainConstants.isKnownProtocol(tx.getFrom()) || 
                   BaseChainConstants.isKnownProtocol(tx.getTo())) {
            return BaseChainConstants.CONTRACT_INTERACTION;
        } else {
            return BaseChainConstants.REGULAR_TRANSFER;
        }
    }
    
    /**
     * Check if transaction is a whale transaction (>10 ETH).
     * @param tx Transaction to check
     * @return true if whale, false otherwise
     */
    private boolean isWhaleTransaction(Transaction tx) {
        if (tx == null || tx.getValue() == null) {
            return false;
        }
        return tx.getValue().compareTo(EthUnitConverter.etherStringToWei(String.valueOf(BaseChainConstants.WHALE_THRESHOLD))) > 0;
    }
    
    /**
     * Get worker statistics with BigDecimal precision.
     */
    public String getWorkerStatistics() {
        double whaleRate = processedCount.get() > 0 ? 
                (double) whaleCount.get() / processedCount.get() * 100 : 0.0;
        
        return String.format(
                "WhaleAnalysisWorker Statistics:\n" +
                "- Processed: %d\n" +
                "- Whales: %d (%.2f%%)\n" +
                "- Errors: %d\n" +
                "- Whale Rate: %.2f%%",
                processedCount.get(), whaleCount.get(), whaleRate, errorCount.get(), whaleRate
        );
    }
}
