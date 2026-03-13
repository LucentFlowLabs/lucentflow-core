package com.lucentflow.indexer.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.constant.RugRiskLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateless service for tracing contract creator funding sources.
 * Virtual Thread friendly and designed for Anti-Rug Pull detection.
 * Enhanced with Basescan API integration for genesis funding trace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorFundingTracer {
    
    private final Web3j web3j;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${lucentflow.basescan.api-key:}")
    private String basescanApiKey;
    
    @Value("${lucentflow.basescan.base-url:https://api.basescan.org/api}")
    private String basescanBaseUrl;
    
    // Internal cache to avoid repeated RPC calls
    private final Map<String, String> creatorCache = new ConcurrentHashMap<>();
    
    // Genesis sender cache to avoid duplicate API calls
    private final Map<String, String> genesisSenderCache = new ConcurrentHashMap<>();
    
    // Enhanced known addresses with Base Chain major entries
    private static final Map<String, RugRiskLevel> KNOWN_ADDRESSES = Map.of(
        "0x4210000000000000000000000000000000000000002", RugRiskLevel.LOW,  // Optimism Bridge
        "0x4200000000000000000000000000000000000000010", RugRiskLevel.LOW,  // Base Bridge
        "0x0000000000000000000000000000000000000000000", RugRiskLevel.LOW,  // Zero Address
        "0x1234567890123456789012345678901234567890", RugRiskLevel.LOW,    // Coinbase Hot Wallet (placeholder)
        "0xabcdef1234567890123456789012345678901234", RugRiskLevel.LOW,    // Binance Bridge (placeholder)
        "0xfedcba0987654321098765432109876543210987", RugRiskLevel.CRITICAL // Tornado Cash Base (placeholder)
    );

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
                // Early exit if not a contract creation
                if (!entity.getIsContractCreation()) {
                    return entity;
                }
                
                String creatorAddress = entity.getFromAddress();
                String cacheKey = creatorAddress.toLowerCase();
                
                // Check cache first
                String cachedResult = creatorCache.get(cacheKey);
                if (cachedResult != null) {
                    String[] parts = cachedResult.split(":");
                    entity.setFundingSourceTag(parts[0]);
                    entity.setRugRiskLevel(parts[1]);
                    log.debug("Cache hit for creator {}: {} - {}", creatorAddress, parts[0], parts[1]);
                    return entity;
                }
                
                // Check known addresses first (MVP Strategy)
                RugRiskLevel knownRisk = KNOWN_ADDRESSES.get(cacheKey);
                if (knownRisk != null) {
                    String tag = getKnownAddressTag(cacheKey);
                    entity.setFundingSourceTag(tag);
                    entity.setRugRiskLevel(knownRisk.name());
                    
                    // Cache the result
                    creatorCache.put(cacheKey, tag + ":" + knownRisk.name());
                    
                    log.info("Known address detected: {} - {} ({})", creatorAddress, tag, knownRisk.name());
                    return entity;
                }
                
                // Nonce Check Strategy
                BigInteger nonce = getTransactionCount(creatorAddress);
                if (nonce != null) {
                    if (nonce.compareTo(BigInteger.valueOf(50)) > 0) {
                        // High nonce = established account
                        entity.setFundingSourceTag("ESTABLISHED_ACCOUNT");
                        entity.setRugRiskLevel(RugRiskLevel.LOW.name());
                        creatorCache.put(cacheKey, "ESTABLISHED_ACCOUNT:LOW");
                    } else if (nonce.compareTo(BigInteger.valueOf(10)) <= 0) {
                        // Brand new wallet - call Basescan API for genesis trace
                        String genesisSender = fetchGenesisSender(creatorAddress);
                        if (genesisSender != null) {
                            entity.setFundingSourceAddress(genesisSender);
                            
                            // Analyze genesis sender
                            RugRiskLevel genesisRisk = analyzeGenesisSender(genesisSender);
                            String genesisTag = getGenesisSenderTag(genesisSender, genesisRisk);
                            
                            entity.setFundingSourceTag(genesisTag);
                            entity.setRugRiskLevel(genesisRisk.name());
                            
                            // Cache the result
                            creatorCache.put(cacheKey, genesisTag + ":" + genesisRisk.name());
                            
                            log.info("Genesis trace for {}: {} - {} (nonce: {}, genesis: {})", 
                                    creatorAddress, genesisTag, genesisRisk.name(), nonce, genesisSender);
                        } else {
                            // API failed - fallback
                            entity.setFundingSourceTag("API_FAILED");
                            entity.setRugRiskLevel(RugRiskLevel.UNKNOWN.name());
                            creatorCache.put(cacheKey, "API_FAILED:UNKNOWN");
                        }
                    } else {
                        // Medium nonce = potential new player
                        entity.setFundingSourceTag("POTENTIAL_NEW_PLAYER");
                        entity.setRugRiskLevel(RugRiskLevel.MEDIUM.name());
                        creatorCache.put(cacheKey, "POTENTIAL_NEW_PLAYER:MEDIUM");
                        
                        // Warning log for fresh wallet deployments
                        log.warn("[RUG-SCAN] Warning: Contract {} deployed by fresh wallet {} (nonce: {})!", 
                                entity.getHash(), creatorAddress, nonce);
                    }
                    
                    log.info("Nonce analysis for {}: {} - {} (nonce: {})", 
                            creatorAddress, entity.getFundingSourceTag(), entity.getRugRiskLevel(), nonce);
                } else {
                    // Fallback if nonce check fails
                    entity.setFundingSourceTag("UNKNOWN_SOURCE");
                    entity.setRugRiskLevel(RugRiskLevel.UNKNOWN.name());
                    creatorCache.put(cacheKey, "UNKNOWN_SOURCE:UNKNOWN");
                }
                
                return entity;
                
            } catch (Exception e) {
                log.error("Failed to enrich rug metrics for transaction {}", entity.getHash(), e);
                // Return original entity on failure to prevent pipeline disruption
                return entity;
            }
        });
    }
    
    /**
     * Fetch genesis sender via Basescan API.
     */
    private String fetchGenesisSender(String address) {
        try {
            // Guard logic for missing API key
            if (basescanApiKey == null || basescanApiKey.isEmpty()) {
                log.warn("[RUG-TRACE] Basescan API key missing, deep tracing is disabled.");
                return null;
            }
            
            String cacheKey = address.toLowerCase();
            String cachedSender = genesisSenderCache.get(cacheKey);
            if (cachedSender != null) {
                log.debug("Genesis sender cache hit for {}: {}", address, cachedSender);
                return cachedSender;
            }
            
            String url = String.format("%s?module=account&action=txlist&address=%s&startblock=0&endblock=99999999&page=1&offset=1&sort=asc&apikey=%s",
                    basescanBaseUrl, address, basescanApiKey);
            
            log.debug("Calling Basescan API for address: {}", address);
            BasescanResponse response = restTemplate.getForObject(url, BasescanResponse.class);
            
            if (response != null && response.getStatus().equals("1") && 
                response.getResult() != null && !response.getResult().isEmpty()) {
                
                String genesisSender = response.getResult().get(0).getFrom();
                genesisSenderCache.put(cacheKey, genesisSender);
                
                log.info("Genesis sender found for {}: {}", address, genesisSender);
                return genesisSender;
            } else {
                log.warn("Basescan API returned empty or error response for address: {}", address);
                return null;
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch genesis sender for address {}: {}", address, e.getMessage());
            return null;
        }
    }
    
    /**
     * Analyze genesis sender for risk assessment.
     */
    private RugRiskLevel analyzeGenesisSender(String genesisSender) {
        String senderKey = genesisSender.toLowerCase();
        
        // Check known addresses
        RugRiskLevel knownRisk = KNOWN_ADDRESSES.get(senderKey);
        if (knownRisk != null) {
            return knownRisk;
        }
        
        // Check for common CEX patterns (simplified)
        if (isCexAddress(genesisSender)) {
            return RugRiskLevel.LOW;
        }
        
        // Check for mixer patterns
        if (isMixerAddress(genesisSender)) {
            return RugRiskLevel.CRITICAL;
        }
        
        // Unknown wallet - layering risk
        return RugRiskLevel.MEDIUM;
    }
    
    /**
     * Get human-readable tag for genesis sender.
     */
    private String getGenesisSenderTag(String genesisSender, RugRiskLevel risk) {
        String senderKey = genesisSender.toLowerCase();
        
        // Known addresses
        String knownTag = getKnownAddressTag(senderKey);
        if (!"Known Address".equals(knownTag)) {
            return knownTag;
        }
        
        // CEX detection
        if (isCexAddress(genesisSender)) {
            return "CEX_FUNDING";
        }
        
        // Mixer detection
        if (isMixerAddress(genesisSender)) {
            return "MIXER_FUNDING";
        }
        
        // Unknown wallet
        return switch (risk) {
            case MEDIUM -> "UNKNOWN_WALLET_LAYERING";
            case LOW -> "UNKNOWN_WALLET_LOW_RISK";
            case CRITICAL -> "UNKNOWN_WALLET_HIGH_RISK";
            default -> "UNKNOWN_WALLET";
        };
    }
    
    /**
     * Simple CEX address detection (can be enhanced with actual CEX address lists).
     */
    private boolean isCexAddress(String address) {
        // Placeholder logic - in production, use actual CEX address databases
        return address.startsWith("0x1234") || address.startsWith("0x5678");
    }
    
    /**
     * Simple mixer address detection (can be enhanced with actual mixer address lists).
     */
    private boolean isMixerAddress(String address) {
        // Placeholder logic - in production, use actual mixer address databases
        return address.startsWith("0xfedc") || address.contains("tornado");
    }
    
    /**
     * Get transaction count (nonce) for an address.
     */
    private BigInteger getTransactionCount(String address) {
        try {
            EthGetTransactionCount transactionCount = web3j.ethGetTransactionCount(
                address, DefaultBlockParameter.valueOf("latest")).send();
            
            if (transactionCount != null && transactionCount.getTransactionCount() != null) {
                return transactionCount.getTransactionCount();
            }
        } catch (Exception e) {
            log.warn("Failed to get transaction count for address {}: {}", address, e.getMessage());
        }
        return null;
    }
    
    /**
     * Get human-readable tag for known addresses.
     */
    private String getKnownAddressTag(String address) {
        return switch (address.toLowerCase()) {
            case "0x4210000000000000000000000000000000000000002" -> "Optimism Bridge";
            case "0x4200000000000000000000000000000000000000010" -> "Base Bridge";
            case "0x0000000000000000000000000000000000000000000" -> "Zero Address";
            case "0x1234567890123456789012345678901234567890" -> "Coinbase Hot Wallet";
            case "0xabcdef1234567890123456789012345678901234" -> "Binance Bridge";
            case "0xfedcba0987654321098765432109876543210987" -> "Tornado Cash Base";
            default -> "Known Address";
        };
    }
    
    /**
     * Basescan API response DTOs.
     */
    @Data
    public static class BasescanResponse {
        private String status;
        private String message;
        private List<BasescanTransaction> result;
    }
    
    @Data
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
