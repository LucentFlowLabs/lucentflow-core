package com.lucentflow.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lucentflow.analyzer.service.RiskEngine;
import com.lucentflow.api.dto.RiskScoreRequest;
import com.lucentflow.api.dto.RiskScoreResponse;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import com.lucentflow.common.utils.Sha256HexDigest;
import com.lucentflow.pipeline.FundingTracerPort;
import com.lucentflow.pipeline.GenesisTraceOutcome;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * On-demand address/tx risk lookup. Uses a dedicated RPC permit pool so paid
 * point queries cannot starve the indexer governor.
 *
 * <p>{@code score} is produced by {@link RiskEngine#complete} — the same finish step as ingest.
 * This API always attempts receipt + genesis trace; the worker may skip those on the hot path,
 * so an indexed row's persisted {@code risk_score} can be lower than this live {@code score}.</p>
 *
 * @author ArchLucent
 * @since 1.2
 */
@Slf4j
@Service
public class RiskScoreService {

    static final String COVERAGE_INDEXED = "indexed";
    static final String COVERAGE_PARTIAL = "partial";
    static final String COVERAGE_UNKNOWN = "unknown";

    private final RiskEngine riskEngine;
    private final FundingTracerPort fundingTracer;
    private final WhaleTransactionRepository whaleTransactionRepository;
    private final Web3j web3j;
    private final Semaphore rpcPermits;
    private final long timeoutMs;
    private final long cacheTtlMs;
    private final ExecutorService lookupExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Cache<String, RiskScoreResponse> cache;

    @Autowired
    public RiskScoreService(
            RiskEngine riskEngine,
            FundingTracerPort fundingTracer,
            WhaleTransactionRepository whaleTransactionRepository,
            @Autowired(required = false) Web3j web3j,
            @Value("${lucentflow.api.risk-score.max-concurrent:4}") int maxConcurrent,
            @Value("${lucentflow.api.risk-score.timeout-ms:8000}") long timeoutMs,
            @Value("${lucentflow.api.risk-score.cache-ttl-ms:60000}") long cacheTtlMs,
            @Value("${lucentflow.api.risk-score.cache-max-size:256}") int cacheMaxSize
    ) {
        this.riskEngine = riskEngine;
        this.fundingTracer = fundingTracer;
        this.whaleTransactionRepository = whaleTransactionRepository;
        this.web3j = web3j;
        this.rpcPermits = new Semaphore(Math.max(1, maxConcurrent), true);
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.cacheTtlMs = Math.max(0L, cacheTtlMs);
        this.cache = this.cacheTtlMs <= 0
                ? null
                : Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMillis(this.cacheTtlMs))
                        .maximumSize(Math.max(1L, cacheMaxSize))
                        .executor(Runnable::run)
                        .build();
    }

    @PreDestroy
    void shutdown() {
        lookupExecutor.shutdownNow();
    }

    public RiskScoreResponse score(RiskScoreRequest request) {
        String address = normalizeAddress(request == null ? null : request.address());
        String txHash = normalizeHash(request == null ? null : request.txHash());
        if (address == null && txHash == null) {
            throw new IllegalArgumentException("address or txHash is required");
        }
        String cacheKey = cacheKey(address, txHash);
        if (cache != null) {
            RiskScoreResponse cached = cache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        if (!rpcPermits.tryAcquire()) {
            throw new RiskScoreBusyException("Risk score RPC permit pool exhausted");
        }
        CompletableFuture<RiskScoreResponse> future = CompletableFuture.supplyAsync(
                () -> scoreBlocking(address, txHash), lookupExecutor);
        try {
            RiskScoreResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (cache != null) {
                cache.put(cacheKey, response);
            }
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RiskScoreTimeoutException("Risk score lookup timed out", e);
        } catch (RiskScoreBusyException | RiskScoreTimeoutException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Risk score lookup failed", cause);
        } finally {
            rpcPermits.release();
        }
    }

    private RiskScoreResponse scoreBlocking(String address, String txHash) {
        WhaleTransaction indexed = loadIndexed(address, txHash);
        Transaction rpcTx = fetchTransaction(txHash != null ? txHash : (indexed == null ? null : indexed.getHash()));
        if (rpcTx != null && address == null) {
            address = normalizeAddress(rpcTx.getFrom());
        }
        if (address == null && indexed != null) {
            address = normalizeAddress(indexed.getFromAddress());
        }

        WhaleTransaction subject = indexed != null ? copyForScoring(indexed) : new WhaleTransaction();
        if (rpcTx != null) {
            applyRpcTransaction(subject, rpcTx);
        } else if (indexed == null && address != null) {
            subject.setFromAddress(address);
            subject.setIsContractCreation(Boolean.FALSE);
        }

        int recentDeployments = countRecentDeployments(subject.getFromAddress());
        String bytecodeHash = subject.getBytecodeHash();
        if (bytecodeHash == null && rpcTx != null && Boolean.TRUE.equals(subject.getIsContractCreation())) {
            bytecodeHash = Sha256HexDigest.hashUtf8(rpcTx.getInput());
            subject.setBytecodeHash(bytecodeHash);
        }
        int cloneCount = countClones(bytecodeHash);

        applyReceiptRevert(subject, rpcTx);

        GenesisTraceOutcome origin = traceOrigin(address != null ? address : subject.getFromAddress());
        if (origin != null) {
            if (origin.fundingSourceAddress() != null) {
                subject.setFundingSourceAddress(origin.fundingSourceAddress());
            }
            if (origin.fundingSourceTag() != null) {
                subject.setFundingSourceTag(origin.fundingSourceTag());
            }
        }

        RiskEngine.RiskAssessment assessment = riskEngine.calculateRisk(
                subject, rpcTx, recentDeployments, cloneCount);
        boolean blacklisted = origin != null && origin.blacklisted();
        boolean reverted = "REVERTED".equals(subject.getExecutionStatus());
        RiskEngine.CompletedScore scored = riskEngine.complete(assessment, reverted, blacklisted);

        Long asOfBlock = resolveAsOfBlock(indexed, rpcTx);
        String coverage = resolveCoverage(indexed, rpcTx, origin);
        String txHashOut = txHash != null ? txHash : subject.getHash();
        return new RiskScoreResponse(
                RiskEngine.MODEL_VERSION,
                scored.score(),
                scored.rawScore(),
                scored.reasons(),
                address,
                txHashOut,
                bytecodeHash,
                cloneCount,
                origin == null ? 0 : Math.max(0, origin.layersTraced()),
                origin == null ? subject.getFundingSourceAddress() : origin.fundingSourceAddress(),
                origin == null ? subject.getFundingSourceTag() : origin.fundingSourceTag(),
                origin == null ? null : origin.blacklisted(),
                asOfBlock,
                Instant.now(),
                coverage,
                subject.getRugRiskLevel()
        );
    }

    private WhaleTransaction loadIndexed(String address, String txHash) {
        if (txHash != null) {
            Optional<WhaleTransaction> byHash = whaleTransactionRepository.findByHash(txHash);
            if (byHash.isPresent()) {
                return byHash.get();
            }
        }
        if (address != null) {
            return whaleTransactionRepository
                    .findFirstByFromAddressOrToAddressOrderByTimestampDesc(address, address)
                    .orElse(null);
        }
        return null;
    }

    private WhaleTransaction copyForScoring(WhaleTransaction source) {
        WhaleTransaction copy = new WhaleTransaction();
        copy.setHash(source.getHash());
        copy.setFromAddress(source.getFromAddress());
        copy.setToAddress(source.getToAddress());
        copy.setBlockNumber(source.getBlockNumber());
        copy.setIsContractCreation(source.getIsContractCreation());
        copy.setBytecodeHash(source.getBytecodeHash());
        copy.setRugRiskLevel(source.getRugRiskLevel());
        copy.setFundingSourceAddress(source.getFundingSourceAddress());
        copy.setFundingSourceTag(source.getFundingSourceTag());
        copy.setExecutionStatus(source.getExecutionStatus());
        copy.setTimestamp(source.getTimestamp());
        return copy;
    }

    private void applyRpcTransaction(WhaleTransaction subject, Transaction rpcTx) {
        subject.setHash(rpcTx.getHash());
        subject.setFromAddress(normalizeAddress(rpcTx.getFrom()));
        subject.setToAddress(normalizeAddress(rpcTx.getTo()));
        boolean creation = rpcTx.getTo() == null || rpcTx.getTo().isBlank();
        subject.setIsContractCreation(creation);
        if (rpcTx.getBlockNumber() != null) {
            subject.setBlockNumber(rpcTx.getBlockNumber().longValue());
        }
        if (creation) {
            subject.setBytecodeHash(Sha256HexDigest.hashUtf8(rpcTx.getInput()));
        }
    }

    private void applyReceiptRevert(WhaleTransaction subject, Transaction rpcTx) {
        if (rpcTx == null || web3j == null || rpcTx.getHash() == null) {
            return;
        }
        try {
            Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(rpcTx.getHash())
                    .send()
                    .getTransactionReceipt();
            if (receipt.isEmpty()) {
                return;
            }
            String status = receipt.get().getStatus();
            if ("0x0".equals(status) || "0".equals(status)) {
                subject.setExecutionStatus("REVERTED");
            }
        } catch (Exception e) {
            log.debug("[RISK-SCORE] receipt lookup failed hash={} err={}", rpcTx.getHash(), e.getMessage());
        }
    }

    private Transaction fetchTransaction(String hash) {
        if (web3j == null || hash == null || hash.isBlank()) {
            return null;
        }
        try {
            return web3j.ethGetTransactionByHash(hash).send().getTransaction().orElse(null);
        } catch (Exception e) {
            log.debug("[RISK-SCORE] tx lookup failed hash={} err={}", hash, e.getMessage());
            return null;
        }
    }

    private GenesisTraceOutcome traceOrigin(String address) {
        if (address == null || fundingTracer == null) {
            return null;
        }
        try {
            return fundingTracer.traceOriginAsync(address)
                    .get(Math.max(1L, timeoutMs / 2), TimeUnit.MILLISECONDS)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("[RISK-SCORE] genesis trace failed address={} err={}", address, e.getMessage());
            return null;
        }
    }

    private Long resolveAsOfBlock(WhaleTransaction indexed, Transaction rpcTx) {
        if (rpcTx != null && rpcTx.getBlockNumber() != null) {
            return rpcTx.getBlockNumber().longValue();
        }
        if (indexed != null && indexed.getBlockNumber() != null) {
            return indexed.getBlockNumber();
        }
        if (web3j == null) {
            return null;
        }
        try {
            BigInteger block = web3j.ethBlockNumber().send().getBlockNumber();
            return block == null ? null : block.longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCoverage(WhaleTransaction indexed, Transaction rpcTx, GenesisTraceOutcome origin) {
        if (indexed != null && (rpcTx != null || origin != null)) {
            return COVERAGE_INDEXED;
        }
        if (indexed != null) {
            return COVERAGE_INDEXED;
        }
        if (rpcTx != null || origin != null) {
            return COVERAGE_PARTIAL;
        }
        return COVERAGE_UNKNOWN;
    }

    private int countRecentDeployments(String fromAddress) {
        if (fromAddress == null) {
            return 0;
        }
        try {
            Instant since = Instant.now().minus(10, ChronoUnit.MINUTES);
            long n = whaleTransactionRepository.countRecentDeployments(fromAddress, since);
            return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
        } catch (Exception e) {
            return 0;
        }
    }

    private int countClones(String bytecodeHash) {
        if (bytecodeHash == null || bytecodeHash.isBlank()) {
            return 0;
        }
        try {
            Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
            long n = whaleTransactionRepository.countByBytecodeHashSince(bytecodeHash, since);
            return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return hash.trim().toLowerCase(Locale.ROOT);
    }

    private static String cacheKey(String address, String txHash) {
        return (address == null ? "-" : address) + "|" + (txHash == null ? "-" : txHash);
    }

    void cleanUpCache() {
        if (cache != null) {
            cache.cleanUp();
        }
    }

    long cacheSize() {
        return cache == null ? 0L : cache.estimatedSize();
    }
}
