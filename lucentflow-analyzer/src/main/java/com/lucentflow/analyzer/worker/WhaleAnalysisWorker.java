package com.lucentflow.analyzer.worker;

import com.lucentflow.common.constant.BaseChainConstants;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.lease.LeadershipGate;
import com.lucentflow.common.utils.EthUnitConverter;
import com.lucentflow.common.utils.Erc20Decoder;
import com.lucentflow.common.utils.Sha256HexDigest;
import com.lucentflow.common.pipeline.PipedTransaction;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.common.pipeline.WhaleIngressFilter;
import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.analyzer.service.AlertService;
import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.analyzer.service.RiskEngine;
import com.lucentflow.analyzer.service.TagInferenceEngine;
import com.lucentflow.analyzer.service.TagOracleService;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import com.lucentflow.pipeline.BlockSourcePort;
import com.lucentflow.pipeline.FundingTracerPort;
import com.lucentflow.pipeline.WhaleTransactionSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.exceptions.MessageDecodingException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
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
 * Lifecycle is managed via {@link SmartLifecycle} for graceful Spring Boot shutdown.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lucentflow.runtime.enable-analyzer", havingValue = "true", matchIfMissing = true)
public class WhaleAnalysisWorker implements SmartLifecycle {
    
    private final TransactionPipe transactionPipe;
    private final AddressLabeler addressLabeler;
    private final WhaleTransactionSink whaleDatabaseSink;
    private final FundingTracerPort creatorFundingTracer;
    private final RiskEngine riskEngine;
    private final BlockSourcePort blockSource;
    private final WhaleTransactionRepository whaleTransactionRepository;
    private final AlertService alertService;
    private final TagOracleService tagOracleService;
    private final TagInferenceEngine tagInferenceEngine;
    private final FundingTopologyService fundingTopologyService;
    private final LeadershipGate leadershipGate;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong whaleCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicLong catchUpCheckAtMs = new AtomicLong(0);
    private volatile Long cachedBlockLag;

    // T10 Standard: Class-level ExecutorService for nuclear shutdown
    private ExecutorService executor;

    /**
     * Caps concurrent UPSERT batches so drain workers back-pressure the pipe instead of
     * stacking unbounded in-flight writes.
     */
    private Semaphore dbSaveSemaphore = new Semaphore(2);

    private static final long SINK_RETRY_INITIAL_MS = 200L;
    private static final long SINK_RETRY_MAX_MS = 5_000L;

    /**
     * Sleep between UPSERT retries. Package-visible so tests can skip wall-clock backoff.
     *
     * @author ArchLucent
     * @since 1.2
     */
    @FunctionalInterface
    interface SinkRetrySleep {
        void sleep(long millis) throws InterruptedException;
    }

    SinkRetrySleep sinkRetrySleep = Thread::sleep;

    @Value("${lucentflow.analyzer.batch-size:20}")
    private int batchSize;

    @Value("${lucentflow.analyzer.concurrency:2}")
    private int concurrency;

    @Value("${lucentflow.analyzer.catch-up-lag-blocks:500}")
    private long catchUpLagThresholdBlocks = 500L;

    /**
     * Seconds to wait for analysis loops to empty the pipe before stop-thread flush.
     * Package-visible so shutdown tests can skip the wall-clock wait.
     */
    @Value("${lucentflow.analyzer.shutdown-drain-timeout-seconds:120}")
    long shutdownDrainTimeoutSeconds = 120L;

    @Value("${lucentflow.analyzer.shutdown-executor-timeout-seconds:60}")
    long shutdownExecutorTimeoutSeconds = 60L;

    private static final int CATCH_UP_TRACE_MIN_RISK_SCORE = 60;

    /**
     * TTL for the risk-factor memo caches below (milliseconds).
     * A 15-second window covers multiple batch cycles while keeping counts fresh enough
     * for the 10-minute / 7-day query windows used by risk heuristics.
     */
    private static final long RISK_MEMO_TTL_MS = 15_000L;

    /**
     * Short-lived memo caches that prevent N×2 per-transaction DB COUNT queries.
     *
     * <p>Pattern: {@code ConcurrentHashMap<key, long[]{count, expiryEpochMs}>}.
     * {@link ConcurrentHashMap#computeIfAbsent} guarantees a single DB call per unique key
     * even when multiple Virtual Thread workers race for the same address/hash simultaneously.
     * Entries are considered stale after {@link #RISK_MEMO_TTL_MS} and will be re-queried lazily.</p>
     */
    private final ConcurrentHashMap<String, long[]> deployerCountMemo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, long[]> bytecodeCountMemo = new ConcurrentHashMap<>();

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        isShuttingDown.set(false);
        int effectiveConcurrency = Math.max(1, concurrency);
        int effectiveBatchSize = Math.max(1, batchSize);
        log.info("Starting WhaleAnalysisWorker with {} virtual thread workers (batchSize={}).",
                effectiveConcurrency, effectiveBatchSize);

        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < effectiveConcurrency; i++) {
            int workerId = i;
            executor.submit(() -> startAnalysisLoop(workerId));
        }
    }

    @Override
    public void stop() {
        doStop();
    }

    @Override
    public void stop(Runnable callback) {
        try {
            doStop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        // Stop after the indexer (producer) so the pipe can be drained.
        return Integer.MAX_VALUE - 100;
    }

    private void doStop() {
        if (!running.getAndSet(false) && isShuttingDown.get()) {
            return;
        }
        log.info("Graceful shutdown: draining WhaleAnalysisWorker...");
        isShuttingDown.set(true);
        transactionPipe.stopAccepting();

        long drainTimeoutSeconds = Math.max(0L, shutdownDrainTimeoutSeconds);
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(drainTimeoutSeconds);
        while (transactionPipe.hasPending() && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (transactionPipe.hasPending()) {
            log.warn("WhaleAnalysisWorker drain wait elapsed with {} pending txs; last-chance flush on stop thread",
                    transactionPipe.size());
            flushPipeForShutdown();
        } else {
            log.info("WhaleAnalysisWorker drain complete; pipe empty");
        }

        if (executor != null) {
            executor.shutdown();
            long executorTimeoutSeconds = Math.max(1L, shutdownExecutorTimeoutSeconds);
            try {
                if (!executor.awaitTermination(executorTimeoutSeconds, TimeUnit.SECONDS)) {
                    log.warn("WhaleAnalysisWorker executor did not terminate within {}s; forcing shutdownNow",
                            executorTimeoutSeconds);
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("WhaleAnalysisWorker executor still running after force stop");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                log.warn("Interrupted while awaiting WhaleAnalysisWorker termination");
            }
        }

        if (transactionPipe.hasPending()) {
            log.error("Pipe still has {} txs after executor stop; last-chance flush", transactionPipe.size());
            flushPipeForShutdown();
        }

        log.info("WhaleAnalysisWorker shutdown complete. Final stats: {} processed, {} whales detected, {} errors.",
                processedCount.get(), whaleCount.get(), errorCount.get());
    }

    /**
     * Drains whatever is still in the pipe and persists it on the caller thread.
     * Used when analysis loops did not empty the queue before the drain deadline.
     */
    void flushPipeForShutdown() {
        List<PipedTransaction> remaining = transactionPipe.drainAll();
        if (remaining.isEmpty()) {
            return;
        }
        log.warn("Shutdown last-chance flush of {} piped txs", remaining.size());
        ingestDrainedBatch(remaining);
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
                    if (!isShuttingDown.get() && !leadershipGate.isLeader()) {
                        // Follower: do not drain the pipe (leader owns ingest + analysis).
                        // During shutdown this process still owns the in-memory queue.
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
                        continue;
                    }
                    log.debug("Worker-{} polling for next batch...", workerId);
                    List<PipedTransaction> rawBatch = transactionPipe.drainBatch(Math.max(1, batchSize));

                    if (rawBatch.isEmpty()) {
                        if (isShuttingDown.get()) {
                            log.info("Analyzer-Worker-{} exiting after drain (pipe empty).", workerId);
                            break;
                        }
                        // Efficient waiting: pause for 100ms if no data
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                        continue;
                    }

                    ingestDrainedBatch(rawBatch);
                    
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

    /**
     * Enrich, UPSERT, then alert a drained pipe batch. Shared by the analysis loop
     * and shutdown last-chance flush.
     *
     * @param rawBatch items already removed from {@link TransactionPipe}
     */
    void ingestDrainedBatch(List<PipedTransaction> rawBatch) {
        if (rawBatch == null || rawBatch.isEmpty()) {
            return;
        }
        List<CompletableFuture<WhaleTransaction>> futures = rawBatch.stream()
                .map(this::processAndFilterWhaleAsync)
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<WhaleTransaction> whaleBatch = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        if (!whaleBatch.isEmpty()) {
            for (WhaleTransaction w : whaleBatch) {
                tagInferenceEngine.inferCandidateTags(w);
                tagOracleService.applyResolvedTags(w);
            }
            boolean acquired = false;
            try {
                dbSaveSemaphore.acquire();
                acquired = true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                persistThenAlertUntilSuccess(whaleBatch);
                whaleCount.addAndGet(whaleBatch.size());
            } catch (RuntimeException e) {
                errorCount.incrementAndGet();
                log.error("[SINK-RETRY] persist abandoned for batch of {} hashes={}.",
                        whaleBatch.size(), sinkBatchHashes(whaleBatch), e);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            } finally {
                if (acquired) {
                    dbSaveSemaphore.release();
                }
            }
        }

        processedCount.addAndGet(rawBatch.size());
    }

    /**
     * UPSERT first, then alert. Persistence failures propagate and skip alerts.
     *
     * @param whaleBatch enriched whales for this drain cycle
     */
    void persistThenAlert(List<WhaleTransaction> whaleBatch) {
        whaleDatabaseSink.saveWhaleTransactions(whaleBatch);
        for (WhaleTransaction w : whaleBatch) {
            alertService.sendAlertIfNeeded(w);
        }
    }

    /**
     * Retry UPSERT until it succeeds so a transient DB failure cannot drop a drained batch.
     * Alerts still run only after a successful persist. Interrupt triggers one last attempt;
     * if that fails the exception propagates and hashes are logged by the caller.
     *
     * @param whaleBatch enriched whales for this drain cycle
     */
    void persistThenAlertUntilSuccess(List<WhaleTransaction> whaleBatch) {
        if (whaleBatch == null || whaleBatch.isEmpty()) {
            return;
        }
        long backoffMs = SINK_RETRY_INITIAL_MS;
        int attempt = 0;
        while (true) {
            try {
                persistThenAlert(whaleBatch);
                if (attempt > 0) {
                    log.info("[SINK-RETRY] UPSERT succeeded after {} retries for batch of {}.",
                            attempt, whaleBatch.size());
                }
                return;
            } catch (RuntimeException e) {
                attempt++;
                log.warn("[SINK-RETRY] UPSERT failed (attempt {}); retrying {} txs hashes={}: {}",
                        attempt, whaleBatch.size(), sinkBatchHashes(whaleBatch), e.toString());
                if (Thread.currentThread().isInterrupted()) {
                    persistThenAlertLastChance(whaleBatch);
                    return;
                }
                try {
                    sinkRetrySleep.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    persistThenAlertLastChance(whaleBatch);
                    return;
                }
                backoffMs = Math.min(backoffMs * 2, SINK_RETRY_MAX_MS);
            }
        }
    }

    private void persistThenAlertLastChance(List<WhaleTransaction> whaleBatch) {
        persistThenAlert(whaleBatch);
        log.info("[SINK-RETRY] last-chance UPSERT succeeded for batch of {}.", whaleBatch.size());
    }

    private static String sinkBatchHashes(List<WhaleTransaction> whaleBatch) {
        StringBuilder hashes = new StringBuilder();
        for (WhaleTransaction tx : whaleBatch) {
            if (tx == null || tx.getHash() == null) {
                continue;
            }
            if (hashes.length() > 0) {
                hashes.append(',');
            }
            hashes.append(tx.getHash());
        }
        return hashes.toString();
    }

    private CompletableFuture<WhaleTransaction> processAndFilterWhaleAsync(PipedTransaction piped) {
        Transaction tx = piped.transaction();
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
                    .timestamp(piped.blockTimestamp())
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
     * Same ingress rules as the indexer pipe push ({@link WhaleIngressFilter}).
     *
     * @param tx The raw Web3j transaction
     * @return true if the transaction is a whale movement or contract deployment
     */
    private boolean isWhale(Transaction tx) {
        return WhaleIngressFilter.matches(tx);
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
            // - valueEth >= 5.0, or
            // - contract creation (to preserve integrity signal for factories), or
            // - candidate core-token contract (ERC-20 outpost).
            boolean shouldFetchReceipt = Boolean.TRUE.equals(whaleTx.getIsContractCreation())
                    || (value != null && value.compareTo(WhaleIngressFilter.CONTRACT_CALL_THRESHOLD_ETH) >= 0)
                    || tokenCandidate;

            if (!shouldFetchReceipt) {
                return completeScoringWithOptionalGenesis(enrichedTx, tx);
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
            return completeScoringWithOptionalGenesis(enrichedTx, tx);
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

        return completeScoringWithOptionalGenesis(enrichedTx, tx);
    }

    /**
     * Catch-up skip for rug/receipt enrich on smaller transfers. Package-visible for tests.
     *
     * @param valueEth transfer value; {@code null} never skips
     * @return {@code true} when catch-up lag is high and value is below 20 ETH
     */
    boolean shouldSkipTracingInCatchUp(BigDecimal valueEth) {
        if (valueEth == null) {
            return false;
        }
        if (valueEth.compareTo(new BigDecimal("20")) >= 0) {
            return false;
        }
        return isCatchUpMode();
    }

    /**
     * True when head minus last-scanned exceeds {@link #catchUpLagThresholdBlocks}.
     * Fail-open (false) when lag cannot be read so tracing is not skipped on RPC errors.
     * Package-visible for tests.
     *
     * @return {@code true} only when lag was read and exceeds the configured threshold
     */
    boolean isCatchUpMode() {
        Long lag = getBlockLagCached();
        return lag != null && lag > catchUpLagThresholdBlocks;
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
     * Engine heuristics, optional Genesis Trace when ingest risk is elevated, then {@link RiskEngine#complete}.
     *
     * @param whaleTx enriched whale
     * @param tx      raw chain transaction
     * @return scored whale
     */
    private CompletableFuture<WhaleTransaction> completeScoringWithOptionalGenesis(
            WhaleTransaction whaleTx, Transaction tx) {
        RiskEngine.RiskAssessment base = engineAssessment(whaleTx, tx);
        boolean reverted = "REVERTED".equals(whaleTx.getExecutionStatus());
        int gateScore = RiskEngine.rawAfterRevert(base, reverted);
        if (!RiskEngine.shouldTraceGenesis(gateScore)
                || shouldSkipDeepOriginTrace(gateScore)) {
            applyCompletedScoring(whaleTx, base, reverted, false);
            return CompletableFuture.completedFuture(whaleTx);
        }
        String initiator = tx.getFrom();
        if (initiator == null || initiator.isBlank()) {
            applyCompletedScoring(whaleTx, base, reverted, false);
            return CompletableFuture.completedFuture(whaleTx);
        }
        return creatorFundingTracer.traceOriginAsync(initiator).thenApply(opt -> {
            boolean blacklisted = opt.map(o -> {
                whaleTx.setFundingSourceAddress(o.fundingSourceAddress());
                whaleTx.setFundingSourceTag(o.fundingSourceTag());
                fundingTopologyService.recordGenesisEdge(initiator, o, whaleTx.getHash());
                return o.blacklisted();
            }).orElse(false);
            applyCompletedScoring(whaleTx, base, reverted, blacklisted);
            return whaleTx;
        });
    }

    private RiskEngine.RiskAssessment engineAssessment(WhaleTransaction whaleTx, Transaction tx) {
        int recentDeploymentCount = countRecentDeploymentsForInitiator(tx.getFrom());
        int identicalBytecodeCount = countIdenticalBytecodeDeployments(whaleTx.getBytecodeHash());
        return riskEngine.calculateRisk(whaleTx, tx, recentDeploymentCount, identicalBytecodeCount);
    }

    private void applyCompletedScoring(
            WhaleTransaction whaleTx,
            RiskEngine.RiskAssessment base,
            boolean reverted,
            boolean blacklistedFunding) {
        RiskEngine.CompletedScore scored = riskEngine.complete(base, reverted, blacklistedFunding);
        whaleTx.setRiskScore(scored.score());
        whaleTx.setRiskReasons(new LinkedHashMap<>(scored.reasons()));
    }

    /**
     * Catch-up protection: when ingestion lag is high, skip deep origin tracing for lower-risk whales
     * to keep the TransactionPipe draining and prevent [STALL-ALERT]. Package-visible for tests.
     *
     * @param riskScore engine+revert gate score
     * @return {@code true} when lag is high and score is below {@link #CATCH_UP_TRACE_MIN_RISK_SCORE}
     */
    boolean shouldSkipDeepOriginTrace(int riskScore) {
        if (riskScore >= CATCH_UP_TRACE_MIN_RISK_SCORE) {
            return false;
        }
        return isCatchUpMode();
    }

    private Long getBlockLagCached() {
        long now = System.currentTimeMillis();
        long last = catchUpCheckAtMs.get();
        if (now - last < 5_000L) {
            return cachedBlockLag;
        }
        if (!catchUpCheckAtMs.compareAndSet(last, now)) {
            Long cached = cachedBlockLag;
            return cached != null ? cached : computeBlockLagSilently();
        }
        Long lag = computeBlockLagSilently();
        cachedBlockLag = lag;
        return lag;
    }

    private Long computeBlockLagSilently() {
        try {
            long lastScanned = blockSource.getLastScannedBlock();
            long head = blockSource.getLatestBlockNumber();
            return Math.max(0L, head - lastScanned);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Prior persisted rows with the same creation bytecode hash in the last 7 days (clone detector).
     *
     * <p>Results are memoized for {@link #RISK_MEMO_TTL_MS} ms so that multiple transactions
     * sharing the same bytecode fingerprint within a batch window only trigger one DB COUNT.</p>
     */
    private int countIdenticalBytecodeDeployments(String bytecodeHash) {
        if (bytecodeHash == null || bytecodeHash.isBlank()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long[] cached = bytecodeCountMemo.get(bytecodeHash);
        if (cached != null && cached[1] > now) {
            return (int) cached[0];
        }
        try {
            Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
            long n = whaleTransactionRepository.countByBytecodeHashSince(bytecodeHash, since);
            int result = n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
            bytecodeCountMemo.put(bytecodeHash, new long[]{result, now + RISK_MEMO_TTL_MS});
            return result;
        } catch (Exception e) {
            log.debug("[BYTECODE-FP] countByBytecodeHashSince failed for {}: {}", bytecodeHash, e.getMessage());
            return 0;
        }
    }

    /**
     * Serial deployer signal: persisted contract creations from this initiator in the last 10 minutes.
     * Current tx is usually not persisted yet; cross-batch factory behavior still surfaces here.
     *
     * <p>Results are memoized for {@link #RISK_MEMO_TTL_MS} ms — a factory deploying N contracts
     * per batch triggers exactly one COUNT query regardless of N.</p>
     */
    private int countRecentDeploymentsForInitiator(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long[] cached = deployerCountMemo.get(fromAddress);
        if (cached != null && cached[1] > now) {
            return (int) cached[0];
        }
        try {
            Instant since = Instant.now().minus(10, ChronoUnit.MINUTES);
            long n = whaleTransactionRepository.countRecentDeployments(fromAddress, since);
            int result = n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
            deployerCountMemo.put(fromAddress, new long[]{result, now + RISK_MEMO_TTL_MS});
            return result;
        } catch (Exception e) {
            log.warn("[SERIAL-DEPLOYER] countRecentDeployments failed for {}: {}", fromAddress, e.getMessage());
            return 0;
        }
    }
}