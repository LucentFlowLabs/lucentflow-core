package com.lucentflow.analyzer.worker;

import com.lucentflow.common.constant.BaseChainConstants;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.utils.EthUnitConverter;
import com.lucentflow.common.utils.Erc20Decoder;
import com.lucentflow.common.utils.Sha256HexDigest;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.analyzer.service.AlertService;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import com.lucentflow.indexer.source.BaseBlockSource;
import com.lucentflow.indexer.sink.WhaleDatabaseSink;
import com.lucentflow.indexer.service.CreatorFundingTracer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.exceptions.MessageDecodingException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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
    private final BaseBlockSource blockSource;
    private final WhaleTransactionRepository whaleTransactionRepository;
    private final AlertService alertService;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong whaleCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private final AtomicLong catchUpCheckAtMs = new AtomicLong(0);
    private volatile boolean catchUpMode = false;

    // T10 Standard: Class-level ExecutorService for nuclear shutdown
    private ExecutorService executor;

    // Async sink backpressure: allow a small number of in-flight batch writes.
    // Non-final to avoid being pulled into Lombok-generated constructor params.
    private Semaphore dbSaveSemaphore = new Semaphore(2);

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
                        int batchSize = whaleBatch.size();
                        whaleCount.addAndGet(batchSize);
                        try {
                            // Don't let DB writes stall transaction draining indefinitely.
                            dbSaveSemaphore.acquire();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        CompletableFuture.runAsync(() -> {
                            try {
                                whaleDatabaseSink.saveWhaleTransactions(whaleBatch);
                                for (WhaleTransaction w : whaleBatch) {
                                    Integer rs = w.getRiskScore();
                                    if (rs != null && rs >= 70) {
                                        alertService.sendHighRiskAlertAsync(w);
                                    }
                                }
                            } finally {
                                dbSaveSemaphore.release();
                            }
                        }, executor).exceptionally(e -> {
                            log.error("[SINK-ASYNC] saveWhaleTransactions failed: {}", e.getMessage());
                            return null;
                        });
                    }

                    processedCount.addAndGet(rawBatch.size());
                    
                    if (processedCount.get() % 1000 == 0) {
                        log.debug("[STATUS] Analyzer throughput: {} processed, {} whales detected.",
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
                    .isContractCreation(tx.getTo() == null || tx.getTo().trim().isEmpty())
                    .timestamp(java.time.Instant.now())
                    .build();

            if (Boolean.TRUE.equals(whaleTx.getIsContractCreation())) {
                String creationInput = tx.getInput();
                String fingerprint = Sha256HexDigest.hashUtf8(creationInput);
                whaleTx.setBytecodeHash(fingerprint);
            }

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
     * Implements strict industrial-grade filtering.
     * 
     * @param tx The raw Web3j transaction
     * @return true if the transaction is a whale movement or contract deployment, false otherwise
     */
    private boolean isWhale(Transaction tx) {
        if (tx == null) return false;
        
        // 1. Always Capture Contract Creations (0 ETH threshold)
        if (tx.getTo() == null || tx.getTo().trim().isEmpty()) {
            log.debug("[FILTER-PASS] High-value/Creation detected: 0 ETH");
            return true;
        }

        // Module 3: candidate ERC-20 calls to tracked Base core tokens (USDC, AERO, DEGEN).
        if (Erc20Decoder.isCoreTokenContract(tx.getTo())) {
            log.debug("[FILTER-PASS] Core token contract interaction (receipt decode required)");
            return true;
        }

        // Handle null values to avoid NPE
        if (tx.getValue() == null) return false;
        
        // Ensure accurate detection of contract calls: input data length > 10 (0x + 8 chars method sig)
        boolean isContractCall = tx.getInput() != null && tx.getInput().length() > 10;
        
        BigDecimal valueInEth = EthUnitConverter.weiToEther(tx.getValue());
        
        if (isContractCall) {
            // Special Exception: Renounce Ownership signature (0x715018a6) -> 0 ETH Threshold
            if (tx.getInput().contains("715018a6")) {
                log.debug("[FILTER-PASS] {} ETH captured", valueInEth);
                return true;
            }
            
            // 2. Contract Calls -> 5.0 ETH Threshold
            if (valueInEth.compareTo(new BigDecimal("5.0")) >= 0) {
                log.debug("[FILTER-PASS] {} ETH captured", valueInEth);
                return true;
            } else {
                log.debug("[FILTER-DROP] Value {} is below threshold for CONTRACT_CALL", valueInEth);
                return false;
            }
        }
        
        // 3. Regular ETH Transfers -> 10.0 ETH Threshold (Hard limit)
        if (valueInEth.compareTo(new BigDecimal("10.0")) >= 0) {
            log.debug("[FILTER-PASS] {} ETH captured", valueInEth);
            return true;
        } else {
            log.debug("[FILTER-DROP] Value {} is below threshold for ETH_TRANSFER", valueInEth);
            return false;
        }
    }

    private CompletableFuture<WhaleTransaction> enrichAsync(WhaleTransaction whaleTx, Transaction tx) {
        return enrichAsyncCore(whaleTx, tx).exceptionallyCompose(ex -> {
            if (!isMessageDecodingGlitch(ex)) {
                return CompletableFuture.failedFuture(ex);
            }
            log.debug("[NODE-GLITCH] RPC decode glitch for tx {}, applying silent retry: {}", tx.getHash(), ex.toString());
            coolDownAfterNodeGlitch(3000L);
            return enrichAsyncCore(whaleTx, tx).exceptionally(retryEx -> {
                if (isMessageDecodingGlitch(retryEx)) {
                    log.debug("[NODE-GLITCH] Silent retry still failed for tx {}: {}", tx.getHash(), retryEx.toString());
                    return whaleTx;
                }
                throw new CompletionException(retryEx);
            });
        });
    }

    /**
     * Enrichment + receipt fetch + risk; failures are surfaced to {@link #enrichAsync} for decode handling.
     */
    private CompletableFuture<WhaleTransaction> enrichAsyncCore(WhaleTransaction whaleTx, Transaction tx) {
        String fromLabel = addressLabeler.getAddressLabel(tx.getFrom());
        String toLabel = addressLabeler.getAddressLabel(tx.getTo());
        BigDecimal value = whaleTx.getValueEth();

        whaleTx.setAddressTag(fromLabel);
        whaleTx.setTransactionCategory(addressLabeler.getTransactionCategory(tx.getFrom(), tx.getTo(), value));
        whaleTx.setWhaleCategory(BaseChainConstants.classifyWhaleSize(value));
        whaleTx.setFromAddressTag(fromLabel);
        whaleTx.setToAddressTag(toLabel);

        CompletableFuture<WhaleTransaction> baseFuture;
        // Anti-Rug Trace: Enrich contract creations with funding analysis
        if (whaleTx.getIsContractCreation()) {
            if (shouldSkipTracingInCatchUp(whaleTx.getValueEth())) {
                baseFuture = CompletableFuture.completedFuture(whaleTx);
            } else {
                baseFuture = creatorFundingTracer.enrichRugMetrics(whaleTx)
                    .orTimeout(120, TimeUnit.SECONDS)
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
                        return whaleTx;
                    })
                    .exceptionally(e -> {
                        log.warn("Failed to enrich rug metrics for contract {}: {}", whaleTx.getHash(), e.getMessage());
                        // Continue processing without rug analysis to prevent pipeline disruption
                        return whaleTx;
                    });
            }
        } else {
            baseFuture = CompletableFuture.completedFuture(whaleTx);
        }

        // Transaction Integrity Audit: execution status + risk; Genesis Trace 2.0 when risk score > 40.
        // Module 3: same receipt is used for ERC-20 Transfer decoding (core token list).
        return baseFuture.thenCompose(enrichedTx -> {
            boolean tokenCandidate = Erc20Decoder.isCoreTokenContract(tx.getTo());
            // Performance budget: avoid receipt fetching for low-value contract calls.
            // Receipt calls are expensive (CU) and we only need them when:
            // - valueEth > 5.0, or
            // - contract creation (to preserve integrity signal for factories), or
            // - candidate core-token contract (ERC-20 outpost).
            boolean shouldFetchReceipt = Boolean.TRUE.equals(whaleTx.getIsContractCreation())
                    || (value != null && value.compareTo(new BigDecimal("5.0")) > 0)
                    || tokenCandidate;

            if (!shouldFetchReceipt) {
                applyRiskScoring(enrichedTx, tx);
                applyRevertRiskAdjustment(enrichedTx);
                return applyGenesisTraceIfHighRisk(enrichedTx, tx);
            }

            return blockSource.fetchTransactionReceiptAsync(tx.getHash())
                    .thenCompose(receiptOpt -> applyReceiptAndErc20Outpost(enrichedTx, tx, receiptOpt, tokenCandidate));
        });
    }

    /**
     * Single receipt fetch: execution status (Module 1) + largest core-token Transfer (Module 3).
     * Drops below-threshold ERC-20 candidates without emitting errors.
     */
    private CompletableFuture<WhaleTransaction> applyReceiptAndErc20Outpost(
            WhaleTransaction enrichedTx,
            Transaction tx,
            Optional<TransactionReceipt> receiptOpt,
            boolean tokenCandidate) {

        TransactionReceipt receipt = receiptOpt.orElse(null);
        if (receipt == null) {
            if (tokenCandidate) {
                return CompletableFuture.completedFuture(null);
            }
            applyRiskScoring(enrichedTx, tx);
            applyRevertRiskAdjustment(enrichedTx);
            return applyGenesisTraceIfHighRisk(enrichedTx, tx);
        }

        enrichedTx.setExecutionStatus(receipt.isStatusOK() ? "SUCCESS" : "REVERTED");

        if (!Boolean.TRUE.equals(enrichedTx.getIsContractCreation())) {
            Optional<Erc20Decoder.DecodedTransfer> dec = Erc20Decoder.findLargestCoreTokenTransfer(receipt);
            if (dec.isPresent()) {
                Erc20Decoder.DecodedTransfer dt = dec.get();
                if (dt.humanAmount().compareTo(Erc20Decoder.MIN_WHALE_TOKEN_UNITS) > 0) {
                    enrichedTx.setTransactionType("ERC20_TRANSFER");
                    enrichedTx.setTokenSymbol(dt.symbol());
                    enrichedTx.setTokenAddress(dt.tokenAddress());
                    enrichedTx.setValueEth(dt.humanAmount());
                    enrichedTx.setFromAddress(dt.from());
                    enrichedTx.setToAddress(dt.to());
                    enrichedTx.setIsContractCreation(false);
                    String fl = addressLabeler.getAddressLabel(enrichedTx.getFromAddress());
                    String tl = addressLabeler.getAddressLabel(enrichedTx.getToAddress());
                    enrichedTx.setFromAddressTag(fl);
                    enrichedTx.setToAddressTag(tl);
                    enrichedTx.setAddressTag(fl);
                    enrichedTx.setTransactionCategory(addressLabeler.getTransactionCategory(
                            enrichedTx.getFromAddress(), enrichedTx.getToAddress(), enrichedTx.getValueEth()));
                    enrichedTx.setWhaleCategory(BaseChainConstants.classifyWhaleSize(enrichedTx.getValueEth()));
                } else {
                    return CompletableFuture.completedFuture(null);
                }
            } else if (tokenCandidate) {
                return CompletableFuture.completedFuture(null);
            }
        }

        applyRiskScoring(enrichedTx, tx);
        applyRevertRiskAdjustment(enrichedTx);
        return applyGenesisTraceIfHighRisk(enrichedTx, tx);
    }

    private boolean shouldSkipTracingInCatchUp(BigDecimal valueEth) {
        if (valueEth == null) {
            return false;
        }
        if (valueEth.compareTo(new BigDecimal("20")) >= 0) {
            return false;
        }
        return isCatchUpMode();
    }

    private boolean isCatchUpMode() {
        long now = System.currentTimeMillis();
        long last = catchUpCheckAtMs.get();
        if (now - last < 5_000L) {
            return catchUpMode;
        }
        if (!catchUpCheckAtMs.compareAndSet(last, now)) {
            return catchUpMode;
        }
        try {
            long lastScanned = blockSource.getLastScannedBlock();
            long head = blockSource.getLatestBlockNumber();
            catchUpMode = (head - lastScanned) > 5_000L;
            return catchUpMode;
        } catch (Exception e) {
            // Fail-open: don't skip tracing if we can't safely determine catch-up state.
            catchUpMode = false;
            return false;
        }
    }

    private static boolean isMessageDecodingGlitch(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof MessageDecodingException) {
                return true;
            }
        }
        return false;
    }

    private static void coolDownAfterNodeGlitch(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Genesis Trace 2.0: for whales with elevated risk, recursively audit funding origin (SQL, max 3 hops).
     * Blacklisted funders bump score further; results are on the entity before batch persist.
     */
    private CompletableFuture<WhaleTransaction> applyGenesisTraceIfHighRisk(WhaleTransaction whaleTx, Transaction tx) {
        Integer score = whaleTx.getRiskScore();
        if (score == null || score <= 40) {
            return CompletableFuture.completedFuture(whaleTx);
        }
        String initiator = tx.getFrom();
        if (initiator == null || initiator.isBlank()) {
            return CompletableFuture.completedFuture(whaleTx);
        }
        return creatorFundingTracer.traceOriginAsync(initiator).thenApply(opt -> {
            opt.ifPresent(o -> {
                whaleTx.setFundingSourceAddress(o.fundingSourceAddress());
                whaleTx.setFundingSourceTag(o.fundingSourceTag());
                if (o.blacklisted()) {
                    int base = whaleTx.getRiskScore() == null ? 0 : whaleTx.getRiskScore();
                    whaleTx.setRiskScore(base + 35);
                    appendRiskReason(whaleTx, "Genesis Trace 2.0: Blacklisted funding source");
                }
            });
            return whaleTx;
        });
    }

    private void appendRiskReason(WhaleTransaction whaleTx, String reason) {
        if (whaleTx == null || reason == null || reason.isBlank()) {
            return;
        }
        String existing = whaleTx.getRiskReasons();
        if (existing == null || existing.isBlank()) {
            whaleTx.setRiskReasons(reason);
        } else if (!existing.contains(reason)) {
            whaleTx.setRiskReasons(existing + " | " + reason);
        }
    }

    /**
     * Applies institutional-grade risk scoring to the transaction based on predefined heuristics.
     * 
     * @param whaleTx The WhaleTransaction entity to score
     * @param tx The raw Web3j transaction
     */
    private void applyRiskScoring(WhaleTransaction whaleTx, Transaction tx) {
        int recentDeploymentCount = countRecentDeploymentsForInitiator(tx.getFrom());
        int identicalBytecodeCount = countIdenticalBytecodeDeployments(whaleTx.getBytecodeHash());
        var riskAssessment = riskEngine.calculateRisk(whaleTx, tx, recentDeploymentCount, identicalBytecodeCount);
        whaleTx.setRiskScore(riskAssessment.score());
        whaleTx.setRiskReasons(riskAssessment.reasons());
    }

    /**
     * Prior persisted rows with the same creation bytecode hash in the last 7 days (clone detector).
     */
    private int countIdenticalBytecodeDeployments(String bytecodeHash) {
        if (bytecodeHash == null || bytecodeHash.isBlank()) {
            return 0;
        }
        try {
            Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
            long n = whaleTransactionRepository.countByBytecodeHashSince(bytecodeHash, since);
            return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
        } catch (Exception e) {
            log.debug("[BYTECODE-FP] countByBytecodeHashSince failed for {}: {}", bytecodeHash, e.getMessage());
            return 0;
        }
    }

    /**
     * Serial deployer signal: persisted contract creations from this initiator in the last 10 minutes.
     * Current tx is usually not persisted yet; cross-batch factory behavior still surfaces here.
     */
    private int countRecentDeploymentsForInitiator(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return 0;
        }
        try {
            Instant since = Instant.now().minus(10, ChronoUnit.MINUTES);
            long n = whaleTransactionRepository.countRecentDeployments(fromAddress, since);
            return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
        } catch (Exception e) {
            log.warn("[SERIAL-DEPLOYER] countRecentDeployments failed for {}: {}", fromAddress, e.getMessage());
            return 0;
        }
    }

    /**
     * Adjusts risk score and reasons for reverted transactions as part of the
     * Transaction Integrity Audit module.
     *
     * @param whaleTx Whale transaction to adjust
     */
    private void applyRevertRiskAdjustment(WhaleTransaction whaleTx) {
        if (whaleTx == null) {
            return;
        }

        if (!"REVERTED".equals(whaleTx.getExecutionStatus())) {
            return;
        }

        Integer baseScore = whaleTx.getRiskScore();
        int adjustedScore = (baseScore == null ? 0 : baseScore) + 40;
        whaleTx.setRiskScore(adjustedScore);

        String baseReasons = whaleTx.getRiskReasons();
        String reason = "On-chain Reverted Transaction (Potential Exploit Attempt)";
        if (baseReasons == null || baseReasons.isBlank()) {
            whaleTx.setRiskReasons(reason);
        } else if (!baseReasons.contains(reason)) {
            whaleTx.setRiskReasons(baseReasons + " | " + reason);
        }
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