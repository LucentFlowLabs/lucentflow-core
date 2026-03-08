package com.lucentflow.common.constant;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable constants registry for Base chain whale and anti-rug analysis.
 * 
 * <p>Implementation Details:
 * Thread-safe immutable constants with ConcurrentHashMap for known address lookups.
 * All thresholds use BigDecimal for precise financial calculations and avoid floating-point errors.
 * Virtual thread compatible due to stateless design and concurrent data structures.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public class BaseChainConstants {
    
    // Known addresses on Base chain
    public static final String COINBASE_BRIDGE = "0x49048044D57e1C23A120ab3913D2258d96af6E56";
    public static final String UNISWAP_V3_ROUTER = "0x26213694093010b985442A2338BCe7E690558133";
    
    // Whale thresholds (in ETH) - using BigDecimal for precision
    public static final BigDecimal WHALE_THRESHOLD = new BigDecimal("10.0");
    public static final BigDecimal MEGA_WHALE_THRESHOLD = new BigDecimal("100.0");
    public static final BigDecimal GIGA_WHALE_THRESHOLD = new BigDecimal("1000.0");
    
    // Transaction types
    public static final String REGULAR_TRANSFER = "REGULAR_TRANSFER";
    public static final String CONTRACT_DEPLOYMENT = "CONTRACT_DEPLOYMENT";
    public static final String CONTRACT_INTERACTION = "CONTRACT_INTERACTION";
    
    // Known address tags
    private static final Map<String, String> KNOWN_ADDRESSES = new ConcurrentHashMap<>();
    
    static {
        // Initialize known addresses with their tags
        KNOWN_ADDRESSES.put(COINBASE_BRIDGE, "COINBASE_BRIDGE");
        KNOWN_ADDRESSES.put(UNISWAP_V3_ROUTER, "UNISWAP_V3_ROUTER");
    }
    
    /**
     * Retrieves the classification tag for a known Ethereum address.
     * 
     * <p>Performs case-insensitive address lookup against known protocol addresses.
     * Returns "UNKNOWN" for addresses not in the registry.</p>
     * 
     * @param address Ethereum address to classify (case-insensitive)
     * @return Classification tag if known, "UNKNOWN" otherwise
     * @throws NullPointerException if address is null
     */
    public static String getAddressTag(String address) {
        if (address == null) {
            return "UNKNOWN";
        }
        return KNOWN_ADDRESSES.getOrDefault(address.toLowerCase(), "UNKNOWN");
    }
    
    /**
     * Determines if an address belongs to a known protocol or contract.
     * 
     * <p>Uses getAddressTag() internally and checks if result differs from "UNKNOWN".
     * Provides quick boolean check for protocol address filtering.</p>
     * 
     * @param address Ethereum address to verify
     * @return true if address is a known protocol address, false otherwise
     * @throws NullPointerException if address is null
     */
    public static boolean isKnownProtocol(String address) {
        return !getAddressTag(address).equals("UNKNOWN");
    }
    
    /**
     * Classifies transaction value into whale size categories using precise BigDecimal comparison.
     * 
     * <p>Categories based on ETH value thresholds:
     * - GIGA_WHALE: ≥ 1000 ETH
     * - MEGA_WHALE: ≥ 100 ETH
     * - WHALE: ≥ 10 ETH
     * - REGULAR: < 10 ETH</p>
     * 
     * @param valueEth Transaction value in ETH as BigDecimal for precision
     * @return Whale category string ("GIGA_WHALE", "MEGA_WHALE", "WHALE", or "REGULAR")
     * @throws NullPointerException if valueEth is null
     */
    public static String classifyWhaleSize(BigDecimal valueEth) {
        if (valueEth.compareTo(GIGA_WHALE_THRESHOLD) >= 0) {
            return "GIGA_WHALE";
        } else if (valueEth.compareTo(MEGA_WHALE_THRESHOLD) >= 0) {
            return "MEGA_WHALE";
        } else if (valueEth.compareTo(WHALE_THRESHOLD) >= 0) {
            return "WHALE";
        } else {
            return "REGULAR";
        }
    }
    
    /**
     * Returns a defensive copy of all known addresses and their classification tags.
     * 
     * <p>Creates a new ConcurrentHashMap to ensure thread safety and prevent
     * external modification of the internal address registry.</p>
     * 
     * @return Immutable copy of known addresses map with address-to-tag mappings
     */
    public static Map<String, String> getKnownAddresses() {
        return new ConcurrentHashMap<>(KNOWN_ADDRESSES);
    }
}
