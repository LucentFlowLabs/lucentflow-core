package com.lucentflow.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Specialized class for Base L2 gas fee calculations with caching and enterprise-grade reliability.
 * 
 * <p>Implementation Details:</p>
 * <ul>
 *   <li>5-second TTL cache for L1 fee responses to reduce network calls</li>
 *   <li>Professional Web3j ABI encoding with proper error handling</li>
 *   <li>Thread-safe concurrent operations</li>
 *   <li>Automatic cache cleanup to prevent memory leaks</li>
 * </ul>
 * 
 * <p>Thread Safety:</p>
 * This class is thread-safe and suitable for concurrent environments.
 * Uses ConcurrentHashMap for cache safety and scheduled cleanup.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
public class BaseGasOracle {

    private static final String BASE_GAS_ORACLE = "0x420000000000000000000000000000000000000F";
    private static final long CACHE_TTL_SECONDS = 5;
    private static final Map<String, CacheEntry> feeCache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    static {
        // Schedule periodic cache cleanup every 30 seconds
        cleanupExecutor.scheduleAtFixedRate(BaseGasOracle::cleanupCache, 30, 30, TimeUnit.SECONDS);
        
        // Add shutdown hook to clean up executor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**
     * Retrieves the L1 data availability fee for a transaction using the Base L2 GasPriceOracle contract.
     * 
     * @param web3j Web3j instance connected to Base network (must not be null)
     * @param rawTxHex Raw transaction hex string for L1 fee calculation (0x-prefixed)
     * @return L1 data availability fee in wei
     * @throws IllegalArgumentException if web3j or rawTxHex is null or invalid
     * @throws com.lucentflow.common.exception.CryptoException if contract call fails or returns error
     * @throws Exception if Web3j communication fails
     */
    public static BigInteger getL1Fee(Web3j web3j, String rawTxHex) throws Exception {
        if (web3j == null) {
            throw new IllegalArgumentException("Web3j instance cannot be null");
        }
        if (rawTxHex == null || rawTxHex.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw transaction hex cannot be null or empty");
        }
        if (!rawTxHex.startsWith("0x")) {
            throw new IllegalArgumentException("Raw transaction hex must be 0x-prefixed");
        }

        // Check cache first
        String cacheKey = rawTxHex;
        CacheEntry cached = feeCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for L1 fee calculation");
            return cached.fee;
        }

        // Cache miss - fetch from contract
        log.debug("Cache miss for L1 fee calculation, fetching from contract");
        BigInteger fee = fetchL1FeeFromContract(web3j, rawTxHex);
        
        // Store in cache
        feeCache.put(cacheKey, new CacheEntry(fee, System.currentTimeMillis() + CACHE_TTL_SECONDS * 1000));
        
        return fee;
    }

    /**
     * Calculates the precise total cost for Base L2 transactions including L2 execution and L1 data availability fees.
     * 
     * @param l2GasLimit L2 transaction gas limit (must be >= 0)
     * @param l2GasPrice L2 gas price in wei (must be >= 0)
     * @param l1Fee L1 data availability fee in wei (must be >= 0)
     * @return Total transaction cost in wei
     * @throws IllegalArgumentException if any parameter is null or negative
     */
    public static BigInteger calculateBaseTotalCost(BigInteger l2GasLimit, BigInteger l2GasPrice, BigInteger l1Fee) {
        // Validate parameters
        Objects.requireNonNull(l2GasLimit, "L2 gas limit cannot be null");
        Objects.requireNonNull(l2GasPrice, "L2 gas price cannot be null");
        Objects.requireNonNull(l1Fee, "L1 fee cannot be null");
        
        if (l2GasLimit.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("L2 gas limit cannot be negative");
        }
        
        if (l2GasPrice.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("L2 gas price cannot be negative");
        }
        
        if (l1Fee.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("L1 fee cannot be negative");
        }
        
        // Calculate total cost
        BigInteger l2Cost = l2GasLimit.multiply(l2GasPrice);
        BigInteger totalCost = l2Cost.add(l1Fee);
        
        return totalCost;
    }

    /**
     * Clears the fee cache manually. Useful for testing or when cache invalidation is needed.
     */
    public static void clearCache() {
        feeCache.clear();
        log.info("L1 fee cache cleared manually");
    }

    /**
     * Gets cache statistics for monitoring and debugging.
     * 
     * @return CacheStats object containing current cache size and hit ratio
     */
    public static CacheStats getCacheStats() {
        int totalEntries = feeCache.size();
        int expiredEntries = (int) feeCache.values().stream()
                .mapToLong(entry -> entry.isExpired() ? 1 : 0)
                .sum();
        int activeEntries = totalEntries - expiredEntries;
        
        return new CacheStats(activeEntries, expiredEntries, totalEntries);
    }

    private static BigInteger fetchL1FeeFromContract(Web3j web3j, String rawTxHex) throws Exception {
        Function function = new Function(
                "getL1Fee",
                List.of(new DynamicBytes(Numeric.hexStringToByteArray(rawTxHex))),
                List.of(new TypeReference<Uint256>() {})
        );

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, BASE_GAS_ORACLE, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST
        ).send();

        if (response.hasError()) {
            throw new com.lucentflow.common.exception.CryptoException("Oracle Error: " + response.getError().getMessage());
        }
        
        List<Type> out = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return (BigInteger) out.get(0).getValue();
    }

    private static void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        // Remove expired entries
        var iterator = feeCache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} expired cache entries", removedCount);
        }
    }

    /**
     * Simple cache entry with expiration time.
     */
    private static class CacheEntry {
        final BigInteger fee;
        final long expirationTime;

        CacheEntry(BigInteger fee, long expirationTime) {
            this.fee = fee;
            this.expirationTime = expirationTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }

    /**
     * Cache statistics for monitoring.
     */
    public static class CacheStats {
        private final int activeEntries;
        private final int expiredEntries;
        private final int totalEntries;

        CacheStats(int activeEntries, int expiredEntries, int totalEntries) {
            this.activeEntries = activeEntries;
            this.expiredEntries = expiredEntries;
            this.totalEntries = totalEntries;
        }

        public int getActiveEntries() {
            return activeEntries;
        }

        public int getExpiredEntries() {
            return expiredEntries;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{active=%d, expired=%d, total=%d}", 
                    activeEntries, expiredEntries, totalEntries);
        }
    }
}
