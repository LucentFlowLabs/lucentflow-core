package com.lucentflow.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web3 security service for address labeling and transaction categorization.
 * 
 * <p>Implementation Details:
 * Provides real-time address classification using known address databases.
 * Thread-safe ConcurrentHashMap for high-performance address lookups.
 * Implements transaction categorization based on address labels and value thresholds.
 * Virtual thread compatible through immutable operations and stateless design.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class AddressLabeler {
    
    // High-value Base chain addresses with labels
    private static final Map<String, String> ADDRESS_LABELS = new ConcurrentHashMap<>();
    
    static {
        // Initialize known addresses with their labels
        ADDRESS_LABELS.put("0x49048044D57e1C23A120ab3913D2258d96af6E56", "Coinbase Proxy");
        ADDRESS_LABELS.put("0x26213694093010b985442A2338BCe7E690558133", "Uniswap V3 Router");
        
        // Add more known addresses for MVP
        ADDRESS_LABELS.put("0x4200000000000000000000000000000000000006", "WETH");
        ADDRESS_LABELS.put("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", "BaseSwap Router");
        ADDRESS_LABELS.put("0x327Df1E0e0B5A90D5A604B2C45B6c9b8F5E3f4B1", "Aerodrome Router");
    }
    
    /**
     * Get label for a known address.
     * @param address Ethereum address (case-insensitive)
     * @return Label if known, otherwise returns formatted address
     */
    public String getAddressLabel(String address) {
        if (address == null) {
            return "CONTRACT_CREATION";
        }
        
        String normalizedAddress = address.toLowerCase();
        String label = ADDRESS_LABELS.get(normalizedAddress);
        
        if (label != null) {
            return label;
        }
        
        // Return formatted address for unknown addresses
        return formatAddress(address);
    }
    
    /**
     * Get transaction category based on address labels and transaction characteristics using BigDecimal precision.
     * @param fromAddress Sender address
     * @param toAddress Recipient address (can be null for contract deployment)
     * @param transactionValue Value in ETH as BigDecimal
     * @return Transaction category
     */
    public String getTransactionCategory(String fromAddress, String toAddress, BigDecimal transactionValue) {
        // Contract deployment detection
        if (toAddress == null) {
            return "CONTRACT_CREATION";
        }
        
        String fromLabel = getAddressLabel(fromAddress);
        String toLabel = getAddressLabel(toAddress);
        
        // Exchange interactions
        if (isExchange(fromLabel) || isExchange(toLabel)) {
            return "EXCHANGE";
        }
        
        // DeFi interactions
        if (isDeFi(fromLabel) || isDeFi(toLabel)) {
            return "DEFI";
        }
        
        // High-risk fresh wallet detection using BigDecimal comparison (simplified - would need nonce check in production)
        if (transactionValue.compareTo(new BigDecimal("50.0")) > 0 && isUnknownAddress(fromLabel)) {
            return "FRESH_WHALE";
        }
        
        return "REGULAR";
    }
    
    /**
     * Check if label represents an exchange.
     * @param label Address label
     * @return true if exchange
     */
    private boolean isExchange(String label) {
        return label.contains("Coinbase") || label.contains("Exchange") || label.contains("Router");
    }
    
    /**
     * Check if label represents a DeFi protocol.
     * @param label Address label
     * @return true if DeFi
     */
    private boolean isDeFi(String label) {
        return label.contains("Uniswap") || label.contains("Aerodrome") || 
               label.contains("BaseSwap") || label.contains("WETH");
    }
    
    /**
     * Check if address is unknown (not in our label database).
     * @param label Address label
     * @return true if unknown address
     */
    private boolean isUnknownAddress(String label) {
        return label.startsWith("0x") && label.length() > 10;
    }
    
    /**
     * Format address for display (first 6 chars + ... + last 4 chars).
     * @param address Full address
     * @return Formatted address
     */
    private String formatAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
    
    /**
     * Get all known addresses for debugging.
     * @return Map of known addresses and their labels
     */
    public Map<String, String> getKnownAddresses() {
        return new ConcurrentHashMap<>(ADDRESS_LABELS);
    }
    
    /**
     * Add a new address label (for runtime updates).
     * @param address Ethereum address
     * @param label Address label
     */
    public void addAddressLabel(String address, String label) {
        ADDRESS_LABELS.put(address.toLowerCase(), label);
        log.info("Added address label: {} -> {}", address, label);
    }
}
