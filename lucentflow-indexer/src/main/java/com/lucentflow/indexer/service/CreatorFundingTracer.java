package com.lucentflow.indexer.service;

import com.lucentflow.common.constant.RugRiskLevel;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.source.BaseBlockSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stateless service for tracing contract creator funding sources and recursive
 * on-chain funding origins (Genesis Trace 2.0) using PostgreSQL recursive CTEs.
 * RPC paths use {@link BaseBlockSource#runWithRpcPermit} for unified backpressure.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class CreatorFundingTracer {

    private static final String BASESCAN_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final Web3j web3j;
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final BaseBlockSource blockSource;
    private final ObjectMapper objectMapper;

    @Value("${lucentflow.basescan.api-key:}")
    private String basescanApiKey;

    @Value("${lucentflow.basescan.base-url:https://api.basescan.org/api}")
    private String basescanBaseUrl;

    private final Map<String, String> creatorCache = new ConcurrentHashMap<>();
    private final Map<String, String> genesisSenderCache = new ConcurrentHashMap<>();

    // Strict throttle: Basescan free tier is far lower than RPC concurrency.
    private final Semaphore basescanSemaphore = new Semaphore(1);

    // Request deduplicator: same address => same in-flight request (strict serial ingestion).
    private final ConcurrentMap<String, CompletableFuture<String>> basescanGenesisInFlight = new ConcurrentHashMap<>();

    /**
     * Genesis Trace 2.0: single-parameter bind; mirrors docs/research/GENESIS_TRACE_V2.sql.
     * Depth capped at three layers in SQL (no unbounded recursion).
     */
    private static final String GENESIS_TRACE_V2_QUERY = """
            WITH RECURSIVE target AS (
                SELECT LOWER(TRIM(?)) AS addr
            ),
            trace AS (
                SELECT
                    1 AS layer,
                    tgt.addr AS recipient_inspected,
                    LOWER(TRIM(w.from_address))::varchar(42) AS funder_address,
                    w.hash::varchar(66) AS inflow_hash,
                    w.block_number AS inflow_block,
                    w.value_eth AS inflow_value_eth
                FROM target tgt
                INNER JOIN LATERAL (
                    SELECT wt.*
                    FROM whale_transactions wt
                    WHERE LOWER(TRIM(wt.to_address)) = tgt.addr
                      AND wt.value_eth > 0
                    ORDER BY wt.block_number ASC, wt.id ASC
                    LIMIT 1
                ) w ON TRUE

                UNION ALL

                SELECT
                    t.layer + 1,
                    t.funder_address AS recipient_inspected,
                    LOWER(TRIM(w2.from_address))::varchar(42),
                    w2.hash::varchar(66),
                    w2.block_number,
                    w2.value_eth
                FROM trace t
                INNER JOIN LATERAL (
                    SELECT wt.*
                    FROM whale_transactions wt
                    WHERE LOWER(TRIM(wt.to_address)) = t.funder_address
                      AND wt.value_eth > 0
                    ORDER BY wt.block_number ASC, wt.id ASC
                    LIMIT 1
                ) w2 ON TRUE
                WHERE t.layer < 3
                  AND t.funder_address IS NOT NULL
            )
            SELECT layer, recipient_inspected, funder_address, inflow_hash, inflow_block, inflow_value_eth
            FROM trace
            ORDER BY layer DESC
            LIMIT 1
            """;

    private static final Map<String, RugRiskLevel> KNOWN_ADDRESSES = Map.of(
            "0x4210000000000000000000000000000000000002", RugRiskLevel.LOW,
            "0x4200000000000000000000000000000000000010", RugRiskLevel.LOW,
            "0x0000000000000000000000000000000000000000", RugRiskLevel.LOW,
            "0x1234567890123456789012345678901234567890", RugRiskLevel.LOW,
            "0xabcdef1234567890123456789012345678901234", RugRiskLevel.LOW,
            "0xfedcba0987654321098765432109876543210987", RugRiskLevel.CRITICAL
    );

    private static final Set<String> BLACKLISTED_FUNDING_SOURCES = Set.of(
            "0xfedcba0987654321098765432109876543210987"
    );

    private final ExecutorService genesisTraceExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public CreatorFundingTracer(Web3j web3j,
                                RestTemplate restTemplate,
                                JdbcTemplate jdbcTemplate,
                                BaseBlockSource blockSource,
                                ObjectMapper objectMapper) {
        this.web3j = web3j;
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.blockSource = blockSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Outcome of Genesis Trace 2.0 (recursive SQL, max three hops).
     *
     * @param fundingSourceAddress ultimate funder after hops (may be null)
     * @param fundingSourceTag     human-readable classification tag
     * @param blacklisted          true if funder matches blacklist / mixer heuristics
     * @param layersTraced         hop count returned by SQL (0 if no row)
     */
    public record GenesisTraceOutcome(
            String fundingSourceAddress,
            String fundingSourceTag,
            boolean blacklisted,
            int layersTraced
    ) {
    }

    /**
     * Runs Genesis Trace 2.0 on a virtual thread: executes recursive CTE against
     * {@code whale_transactions}. No Java recursion — depth is enforced in SQL.
     *
     * @param address initiator / address to trace (EOA or contract)
     * @return async outcome; empty if no inflow rows exist
     */
    public CompletableFuture<Optional<GenesisTraceOutcome>> traceOriginAsync(String address) {
        if (address == null || address.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String normalized = address.trim();
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<SqlTraceRow> rows = jdbcTemplate.query(
                        GENESIS_TRACE_V2_QUERY,
                        ps -> ps.setString(1, normalized),
                        (rs, rowNum) -> mapSqlTraceRow(rs)
                );
                if (rows.isEmpty()) {
                    return Optional.empty();
                }
                SqlTraceRow raw = rows.getFirst();
                if (raw.funderAddress() == null || raw.funderAddress().isBlank()) {
                    return Optional.empty();
                }
                String funder = raw.funderAddress();
                boolean blacklisted = isBlacklistedFundingSource(funder);
                String tag = classifyFundingTag(funder, blacklisted);
                return Optional.of(new GenesisTraceOutcome(funder, tag, blacklisted, raw.layer()));
            } catch (Exception e) {
                log.warn("[GENESIS-V2] traceOriginAsync failed for {}: {}", address, e.getMessage());
                return Optional.empty();
            }
        }, genesisTraceExecutor);
    }

    private record SqlTraceRow(int layer, String funderAddress) {
    }

    private static SqlTraceRow mapSqlTraceRow(ResultSet rs) throws SQLException {
        return new SqlTraceRow(rs.getInt("layer"), rs.getString("funder_address"));
    }

    private boolean isBlacklistedFundingSource(String funder) {
        if (funder == null || funder.isBlank()) {
            return false;
        }
        String k = funder.toLowerCase();
        if (BLACKLISTED_FUNDING_SOURCES.contains(k)) {
            return true;
        }
        RugRiskLevel known = KNOWN_ADDRESSES.get(k);
        if (known == RugRiskLevel.CRITICAL) {
            return true;
        }
        return isMixerAddress(funder);
    }

    private String classifyFundingTag(String funder, boolean blacklisted) {
        if (blacklisted) {
            return "BLACKLISTED_SOURCE";
        }
        String k = funder.toLowerCase();
        if (KNOWN_ADDRESSES.containsKey(k)) {
            return getKnownAddressTag(k);
        }
        if (isCexAddress(funder)) {
            return "CEX_FUNDING";
        }
        if (isMixerAddress(funder)) {
            return "MIXER_FUNDING";
        }
        return "UNKNOWN_WALLET_LAYERING";
    }

    /**
     * Enrich whale transaction with rug analysis metrics asynchronously.
     * Enhanced with Basescan API integration for genesis funding trace.
     *
     * @param entity The whale transaction to enrich
     * @return CompletableFuture with enriched entity
     */
    public CompletableFuture<WhaleTransaction> enrichRugMetrics(WhaleTransaction entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!entity.getIsContractCreation()) {
                    return entity;
                }

                String creatorAddress = entity.getFromAddress();
                String cacheKey = creatorAddress.toLowerCase();

                String cachedResult = creatorCache.get(cacheKey);
                if (cachedResult != null) {
                    String[] parts = cachedResult.split(":");
                    entity.setFundingSourceTag(parts[0]);
                    entity.setRugRiskLevel(parts[1]);
                    log.debug("Cache hit for creator {}: {} - {}", creatorAddress, parts[0], parts[1]);
                    return entity;
                }

                RugRiskLevel knownRisk = KNOWN_ADDRESSES.get(cacheKey);
                if (knownRisk != null) {
                    String tag = getKnownAddressTag(cacheKey);
                    entity.setFundingSourceTag(tag);
                    entity.setRugRiskLevel(knownRisk.name());
                    creatorCache.put(cacheKey, tag + ":" + knownRisk.name());
                    log.info("Known address detected: {} - {} ({})", creatorAddress, tag, knownRisk.name());
                    return entity;
                }

                BigInteger nonce = getTransactionCount(creatorAddress);
                if (nonce != null) {
                    if (nonce.compareTo(BigInteger.valueOf(50)) > 0) {
                        entity.setFundingSourceTag("ESTABLISHED_ACCOUNT");
                        entity.setRugRiskLevel(RugRiskLevel.LOW.name());
                        creatorCache.put(cacheKey, "ESTABLISHED_ACCOUNT:LOW");
                    } else if (nonce.compareTo(BigInteger.valueOf(10)) <= 0) {
                        String genesisSender = fetchGenesisSender(creatorAddress);
                        if (genesisSender != null) {
                            entity.setFundingSourceAddress(genesisSender);
                            RugRiskLevel genesisRisk = analyzeGenesisSender(genesisSender);
                            String genesisTag = getGenesisSenderTag(genesisSender, genesisRisk);
                            entity.setFundingSourceTag(genesisTag);
                            entity.setRugRiskLevel(genesisRisk.name());
                            creatorCache.put(cacheKey, genesisTag + ":" + genesisRisk.name());
                            log.info("Genesis trace for {}: {} - {} (nonce: {}, genesis: {})",
                                    creatorAddress, genesisTag, genesisRisk.name(), nonce, genesisSender);
                        } else {
                            entity.setFundingSourceTag("API_FAILED");
                            entity.setRugRiskLevel(RugRiskLevel.UNKNOWN.name());
                            creatorCache.put(cacheKey, "API_FAILED:UNKNOWN");
                        }
                    } else {
                        entity.setFundingSourceTag("POTENTIAL_NEW_PLAYER");
                        entity.setRugRiskLevel(RugRiskLevel.MEDIUM.name());
                        creatorCache.put(cacheKey, "POTENTIAL_NEW_PLAYER:MEDIUM");
                        log.warn("[RUG-SCAN] Warning: Contract {} deployed by fresh wallet {} (nonce: {})!",
                                entity.getHash(), creatorAddress, nonce);
                    }
                    log.info("Nonce analysis for {}: {} - {} (nonce: {})",
                            creatorAddress, entity.getFundingSourceTag(), entity.getRugRiskLevel(), nonce);
                } else {
                    entity.setFundingSourceTag("UNKNOWN_SOURCE");
                    entity.setRugRiskLevel(RugRiskLevel.UNKNOWN.name());
                    creatorCache.put(cacheKey, "UNKNOWN_SOURCE:UNKNOWN");
                }

                return entity;

            } catch (Exception e) {
                log.error("Failed to enrich rug metrics for transaction {}", entity.getHash(), e);
                return entity;
            }
        });
    }

    private String fetchGenesisSender(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        if (basescanApiKey == null || basescanApiKey.isEmpty()) {
            log.warn("[RUG-TRACE] Basescan API key missing, deep tracing is disabled.");
            return null;
        }

        String cacheKey = address.toLowerCase(Locale.ROOT);
        String cachedSender = genesisSenderCache.get(cacheKey);
        if (cachedSender != null) {
            log.debug("Genesis sender cache hit for {}: {}", address, cachedSender);
            return cachedSender;
        }

        CompletableFuture<String> newFuture = new CompletableFuture<>();
        CompletableFuture<String> existing = basescanGenesisInFlight.putIfAbsent(cacheKey, newFuture);
        boolean initiator = (existing == null);
        CompletableFuture<String> future = (existing != null) ? existing : newFuture;

        if (initiator) {
            CompletableFuture.supplyAsync(() -> fetchGenesisSenderNetwork(address), genesisTraceExecutor)
                    .whenComplete((r, ex) -> {
                        try {
                            if (ex != null) {
                                newFuture.completeExceptionally(ex);
                            } else {
                                newFuture.complete(r);
                            }
                        } finally {
                            basescanGenesisInFlight.remove(cacheKey, newFuture);
                        }
                    });
        }

        try {
            return future.join();
        } catch (Exception ex) {
            if (initiator) {
                log.warn("[BASESCAN] Deduped request failed for {}: {}", address, ex.getMessage());
            }
            return null;
        }
    }

    private String fetchGenesisSenderNetwork(String address) {
        String cacheKey = address.toLowerCase(Locale.ROOT);
        String url = String.format(Locale.ROOT,
                "%s?chainid=8453&module=account&action=txlist&address=%s&startblock=0&endblock=99999999&page=1&offset=1&sort=asc&apikey=%s",
                basescanBaseUrl, address, basescanApiKey);

        boolean acquired = false;
        try {
            basescanSemaphore.acquire();
            acquired = true;

            // Jittered delay + polite pacing (browser-like).
            sleepJittered(200L, 300);

            try {
                BasescanResponse response = restTemplate.exchange(
                                url, HttpMethod.GET, new HttpEntity<>(basescanHeaders()), BasescanResponse.class)
                        .getBody();
                return parseGenesisSenderResponse(address, cacheKey, url, response);
            } catch (Exception ex) {
                if (isHandshakeOrIoError(ex)) {
                    if (isResourceAccessException(ex)) {
                        log.warn("[BASESCAN-GLITCH] Handshake failed, backing off 10s before retry...");
                    }
                    Thread.sleep(10_000L);
                    sleepJittered(200L, 300);
                    try {
                        BasescanResponse response = restTemplate.exchange(
                                        url, HttpMethod.GET, new HttpEntity<>(basescanHeaders()), BasescanResponse.class)
                                .getBody();
                        return parseGenesisSenderResponse(address, cacheKey, url, response);
                    } catch (Exception retryEx) {
                        // silent retry: keep logs clean; raw response is best-effort only.
                        logRawBasescanResponse(url);
                        return null;
                    }
                }
                log.warn("[BASESCAN] Error while extracting response for {}: {}", address, ex.getMessage());
                logRawBasescanResponse(url);
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (acquired) {
                basescanSemaphore.release();
            }
        }
    }

    private String parseGenesisSenderResponse(String address, String cacheKey, String url, BasescanResponse response) {
        if (response == null) {
            logRawBasescanResponse(url);
            return null;
        }

        if (response.getResult() instanceof String errorText) {
            log.error("[BASESCAN] Basescan Error for {}: {}", address, errorText);
            logRawBasescanResponse(url);
            return null;
        }

        List<BasescanTransaction> transactions = extractTransactions(response.getResult());
        if ("1".equals(response.getStatus()) && !transactions.isEmpty()) {
            String genesisSender = transactions.getFirst().getFrom();
            genesisSenderCache.put(cacheKey, genesisSender);
            log.info("Genesis sender found for {}: {}", address, genesisSender);
            return genesisSender;
        }

        log.warn("Basescan API returned empty or error response for address: {} (status={}, message={})",
                address,
                response.getStatus(),
                response.getMessage());
        if (!"1".equals(response.getStatus())) {
            logRawBasescanResponse(url);
        }
        return null;
    }

    private static void sleepJittered(long baseMillis, int jitterMillis) throws InterruptedException {
        Thread.sleep(baseMillis + ThreadLocalRandom.current().nextInt(jitterMillis));
    }

    private static boolean isHandshakeOrIoError(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if ("javax.net.ssl.SSLHandshakeException".equals(name) || "javax.net.ssl.SSLException".equals(name)) {
                return true;
            }
            if ("java.net.SocketException".equals(name) || "java.net.SocketTimeoutException".equals(name)) {
                return true;
            }
            if ("org.springframework.web.client.ResourceAccessException".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isResourceAccessException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ResourceAccessException) {
                return true;
            }
        }
        return false;
    }

    private List<BasescanTransaction> extractTransactions(Object result) {
        if (result == null) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(
                    result,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BasescanTransaction.class)
            );
        } catch (IllegalArgumentException ex) {
            log.warn("[BASESCAN] Failed to parse result payload: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches the raw JSON body for debugging (invalid key, rate limit text inside JSON, etc.).
     */
    private void logRawBasescanResponse(String url) {
        try {
            var entity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(basescanHeaders()), String.class);
            String body = entity.getBody();
            if (body != null && body.length() > 4000) {
                body = body.substring(0, 4000) + "... [truncated]";
            }
            log.warn("[BASESCAN] RAW HTTP status={} body={}", entity.getStatusCode(), body);
        } catch (Exception rawEx) {
            log.warn("[BASESCAN] Could not load raw response: {}", rawEx.getMessage());
        }
    }

    private static HttpHeaders basescanHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, BASESCAN_USER_AGENT);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private RugRiskLevel analyzeGenesisSender(String genesisSender) {
        String senderKey = genesisSender.toLowerCase();
        RugRiskLevel knownRisk = KNOWN_ADDRESSES.get(senderKey);
        if (knownRisk != null) {
            return knownRisk;
        }
        if (isCexAddress(genesisSender)) {
            return RugRiskLevel.LOW;
        }
        if (isMixerAddress(genesisSender)) {
            return RugRiskLevel.CRITICAL;
        }
        return RugRiskLevel.MEDIUM;
    }

    private String getGenesisSenderTag(String genesisSender, RugRiskLevel risk) {
        String senderKey = genesisSender.toLowerCase();
        String knownTag = getKnownAddressTag(senderKey);
        if (!"Known Address".equals(knownTag)) {
            return knownTag;
        }
        if (isCexAddress(genesisSender)) {
            return "CEX_FUNDING";
        }
        if (isMixerAddress(genesisSender)) {
            return "MIXER_FUNDING";
        }
        return switch (risk) {
            case MEDIUM -> "UNKNOWN_WALLET_LAYERING";
            case LOW -> "UNKNOWN_WALLET_LOW_RISK";
            case CRITICAL -> "UNKNOWN_WALLET_HIGH_RISK";
            default -> "UNKNOWN_WALLET";
        };
    }

    private boolean isCexAddress(String address) {
        return address.startsWith("0x1234") || address.startsWith("0x5678");
    }

    private boolean isMixerAddress(String address) {
        return address.startsWith("0xfedc") || address.toLowerCase().contains("tornado");
    }

    private BigInteger getTransactionCount(String address) {
        try {
            return blockSource.runWithRpcPermit(() -> {
                EthGetTransactionCount transactionCount = web3j.ethGetTransactionCount(
                        address, DefaultBlockParameter.valueOf("latest")).send();
                if (transactionCount != null && transactionCount.getTransactionCount() != null) {
                    return transactionCount.getTransactionCount();
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Failed to get transaction count for address {}: {}", address, e.getMessage());
            return null;
        }
    }

    private String getKnownAddressTag(String address) {
        return switch (address.toLowerCase()) {
            case "0x4210000000000000000000000000000000000002" -> "Optimism Bridge";
            case "0x4200000000000000000000000000000000000010" -> "Base Bridge";
            case "0x0000000000000000000000000000000000000000" -> "Zero Address";
            case "0x1234567890123456789012345678901234567890" -> "Coinbase Hot Wallet";
            case "0xabcdef1234567890123456789012345678901234" -> "Binance Bridge";
            case "0xfedcba0987654321098765432109876543210987" -> "Tornado Cash Base";
            default -> "Known Address";
        };
    }

    @PreDestroy
    public void shutdownGenesisTraceExecutor() {
        genesisTraceExecutor.shutdownNow();
    }

    /**
     * Basescan JSON envelope: {@code { "status": "1", "message": "OK", "result": [...] }}.
     * {@code status} is always a string in API responses.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BasescanResponse {
        private String status;
        private String message;
        private Object result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BasescanTransaction {
        private String blockNumber;
        private String timeStamp;
        private String hash;
        private String nonce;
        private String blockHash;
        private String transactionIndex;
        private String from;
        private String to;
        private String value;
        private String gas;
        private String gasPrice;
        private String isError;
        private String txreceipt_status;
        private String input;
        private String contractAddress;
        private String cumulativeGasUsed;
        private String gasUsed;
        private String confirmations;
        private String methodId;
        private String functionName;
    }
}
