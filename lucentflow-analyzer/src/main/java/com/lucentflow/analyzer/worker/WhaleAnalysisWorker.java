package com.lucentflow.analyzer.worker;

import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.indexer.sink.WhaleDatabaseSink;
import com.lucentflow.indexer.service.CreatorFundingTracer;
import com.lucentflow.common.constant.BaseChainConstants;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.utils.EthUnitConverter;
import com.lucentflow.common.pipeline.TransactionPipe;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * High-performance batch-processing whale analyzer.
 * Leverages Java 21 Virtual Threads and SQL Batching for maximum throughput.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleAnalysisWorker implements CommandLineRunner {
    
    private final TransactionPipe transactionPipe;
    private final AddressLabeler addressLabeler;
    private final WhaleDatabaseSink whaleDatabaseSink;
    private final CreatorFundingTracer creatorFundingTracer;
    private final com.lucentflow.analyzer.service.RiskEngine riskEngine;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong whaleCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // T10 Standard: Class-level ExecutorService for nuclear shutdown
    private ExecutorService executor;

    private static final int BATCH_SIZE = 50;
    private static final int CONCURRENCY = 3; // Number of parallel virtual thread workers

    @Override
    public void run(String... args) {
        log.info("Initializing WhaleAnalysisWorker with {} virtual thread workers.", CONCURRENCY);
        
        // T10 Standard: Use class-level ExecutorService for proper lifecycle management
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        
        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                int workerId = i;
                executor.submit(() -> startAnalysisLoop(workerId));
            }
            // Keep the main thread alive for the executor's lifetime
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WhaleAnalysisWorker main thread interrupted.");
        }
    }

    /**
     * Core analysis loop executed by each virtual thread worker.
     * Continuously drains transactions from the pipeline, processes them, and saves whales to the database.
     * 
     * @param workerId The identifier of the virtual thread worker
     */
    private void startAnalysisLoop(int workerId) {
        log.info("Analyzer-Worker-{} (Virtual Thread) started.", workerId);
        
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    log.debug("Worker-{} polling for next batch...", workerId);
                    List<Transaction> rawBatch = transactionPipe.drainBatch(BATCH_SIZE);
                    
                    if (rawBatch.isEmpty()) {
                        // Efficient waiting: pause for 100ms if no data
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                        continue;
                    }

                    // Process batch in memory
                    List<CompletableFuture<WhaleTransaction>> futures = rawBatch.stream()
                            .map(this::processAndFilterWhaleAsync)
                            .toList();

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                    List<WhaleTransaction> whaleBatch = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .toList();

                    if (!whaleBatch.isEmpty()) {
                        // Massive performance gain: SQL Batch Insert
                        whaleDatabaseSink.saveWhaleTransactions(whaleBatch);
                        whaleCount.addAndGet(whaleBatch.size());
                    }

                    processedCount.addAndGet(rawBatch.size());
                    
                    if (processedCount.get() % 1000 < BATCH_SIZE) {
                        log.info("[STATUS] Analyzer throughput: {} processed, {} whales detected.", 
                                processedCount.get(), whaleCount.get());
                    }

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Critical error in Analyzer-Worker-{}: {}", workerId, e.getMessage());
                }
            }
        } catch (Throwable t) {
            log.error("[WORKER-CRASH] Analyzer-Worker-{} crashed with exception", workerId, t);
            log.error("[WORKER-CRASH] Stack trace:", t);
        }
    }

    private CompletableFuture<WhaleTransaction> processAndFilterWhaleAsync(Transaction tx) {
        try {
            // 1. Threshold check
            if (!isWhale(tx)) return CompletableFuture.completedFuture(null);

            // 2. Conversion and Enrichment
            WhaleTransaction whaleTx = WhaleTransaction.builder()
                    .hash(tx.getHash())
                    .fromAddress(tx.getFrom())
                    .toAddress(tx.getTo())
                    .valueEth(EthUnitConverter.weiToEther(tx.getValue()))
                    .blockNumber(tx.getBlockNumber().longValue())
                    .gasPrice(tx.getGasPrice())
                    .isContractCreation(tx.getTo() == null)
                    .build();

            return enrichAsync(whaleTx, tx).exceptionally(e -> {
                log.warn("Failed to process tx {}: {}", tx.getHash(), e.getMessage());
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to process tx {}: {}", tx.getHash(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Evaluates if a transaction qualifies as a whale movement or contract deployment.
     * 
     * @param tx The raw Web3j transaction
     * @return true if the transaction is a whale movement or contract deployment, false otherwise
     */
    private boolean isWhale(Transaction tx) {
        if (tx == null || tx.getValue() == null) return false;
        
        // NEW LOGIC: Always allow contract creations, even if value is 0
        if (tx.getTo() == null) {
            return true; // Contract creation - always pass through for anti-rug analysis
        }
        
        // Regular transfers must meet whale threshold
        return tx.getValue().compareTo(EthUnitConverter.etherStringToWei(
                String.valueOf(BaseChainConstants.WHALE_THRESHOLD))) > 0;
    }

    private CompletableFuture<WhaleTransaction> enrichAsync(WhaleTransaction whaleTx, Transaction tx) {
        String fromLabel = addressLabeler.getAddressLabel(tx.getFrom());
        String toLabel = addressLabeler.getAddressLabel(tx.getTo());
        BigDecimal value = whaleTx.getValueEth();

        whaleTx.setAddressTag(fromLabel);
        whaleTx.setTransactionCategory(addressLabeler.getTransactionCategory(tx.getFrom(), tx.getTo(), value));
        whaleTx.setWhaleCategory(BaseChainConstants.classifyWhaleSize(value));
        whaleTx.setFromAddressTag(fromLabel);
        whaleTx.setToAddressTag(toLabel);
        
        // Anti-Rug Trace: Enrich contract creations with funding analysis
        if (whaleTx.getIsContractCreation()) {
            return creatorFundingTracer.enrichRugMetrics(whaleTx)
                .thenApply(enriched -> {
                    if (enriched != null) {
                        whaleTx.setFundingSourceAddress(enriched.getFundingSourceAddress());
                        whaleTx.setFundingSourceTag(enriched.getFundingSourceTag());
                        whaleTx.setRugRiskLevel(enriched.getRugRiskLevel());
                        
                        // Log risk alerts for high/critical levels
                        if ("HIGH".equals(enriched.getRugRiskLevel()) || "CRITICAL".equals(enriched.getRugRiskLevel())) {
                            log.warn("[RUG-ALERT] High risk contract detected! Creator: {}, Source: {}, Risk: {}", 
                                    whaleTx.getFromAddress(), enriched.getFundingSourceTag(), enriched.getRugRiskLevel());
                        }
                    }
                    applyRiskScoring(whaleTx, tx);
                    return whaleTx;
                })
                .exceptionally(e -> {
                    log.warn("Failed to enrich rug metrics for contract {}: {}", whaleTx.getHash(), e.getMessage());
                    // Continue processing without rug analysis to prevent pipeline disruption
                    applyRiskScoring(whaleTx, tx);
                    return whaleTx;
                });
        } else {
            applyRiskScoring(whaleTx, tx);
            return CompletableFuture.completedFuture(whaleTx);
        }
    }

    /**
     * Applies institutional-grade risk scoring to the transaction based on predefined heuristics.
     * 
     * @param whaleTx The WhaleTransaction entity to score
     * @param tx The raw Web3j transaction
     */
    private void applyRiskScoring(WhaleTransaction whaleTx, Transaction tx) {
        // Apply institutional-grade risk scoring
        var riskAssessment = riskEngine.calculateRisk(whaleTx, tx);
        whaleTx.setRiskScore(riskAssessment.score());
        whaleTx.setRiskReasons(riskAssessment.reasons());
    }

    /**
     * Executes nuclear shutdown with ExecutorService force kill.
     * T10 Standard lifecycle management.
     */
    @PreDestroy
    public void stop() {
        log.info("Nuclear shutdown: Force stopping WhaleAnalysisWorker...");
        isShuttingDown.set(true);
        Thread.currentThread().interrupt();
        
        // T10 Standard: Force kill all virtual thread tasks
        if (executor != null) {
            executor.shutdownNow(); // Force kill all tasks
            log.info("ExecutorService shutdown completed.");
        }
        
        log.info("WhaleAnalysisWorker nuclear shutdown complete. Final stats: {} processed, {} whales detected, {} errors.", 
                processedCount.get(), whaleCount.get(), errorCount.get());
    }
}