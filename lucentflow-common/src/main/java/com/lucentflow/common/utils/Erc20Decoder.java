package com.lucentflow.common.utils;

import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * ERC-20 {@code Transfer} log decoding for Base mainnet core token outpost (USDC, AERO, DEGEN).
 * Values are normalized to human-readable token units (not wei).
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class Erc20Decoder {

    /** Keccak256 topic0 for {@code Transfer(address,address,uint256)} (ERC-20 standard). */
    public static final String TRANSFER_EVENT_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    /**
     * Minimum normalized amount (human token units) to treat as a whale for Module 3.
     * USDC: ~USD equivalent; other listed tokens: same numeric threshold in token units (no price oracle).
     */
    public static final BigDecimal MIN_WHALE_TOKEN_UNITS = new BigDecimal("10000");

    public record TokenMeta(String symbol, int decimals) {
    }

    /**
     * Base mainnet canonical token registry (lowercase 0x-prefixed address).
     */
    public static final Map<String, TokenMeta> CORE_TOKENS;

    static {
        Map<String, TokenMeta> m = new LinkedHashMap<>();
        m.put("0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", new TokenMeta("USDC", 6));
        m.put("0x94017f291504d6ac5ab78698a44d673752e50529", new TokenMeta("AERO", 18));
        m.put("0x4ed4e8615729794721918c9c8540282249ca408b", new TokenMeta("DEGEN", 18));
        CORE_TOKENS = Collections.unmodifiableMap(m);
    }

    private Erc20Decoder() {
    }

    public static boolean isCoreTokenContract(String contractAddress) {
        if (contractAddress == null || contractAddress.isBlank()) {
            return false;
        }
        return CORE_TOKENS.containsKey(normalizeAddress(contractAddress));
    }

    /**
     * Decodes a single ERC-20 {@code Transfer} log if it is a standard transfer from a known core token contract.
     *
     * @param log receipt log (must include topics and data)
     * @return transfer fields with human-normalized value, or empty if not a matching Transfer
     */
    public static Optional<DecodedTransfer> decodeTransferEvent(Log log) {
        if (log == null || log.getTopics() == null || log.getTopics().size() < 3) {
            return Optional.empty();
        }
        String topic0 = log.getTopics().get(0);
        if (topic0 == null || !TRANSFER_EVENT_TOPIC.equalsIgnoreCase(topic0.trim())) {
            return Optional.empty();
        }
        String tokenAddr = log.getAddress();
        if (tokenAddr == null || tokenAddr.isBlank()) {
            return Optional.empty();
        }
        TokenMeta meta = CORE_TOKENS.get(normalizeAddress(tokenAddr));
        if (meta == null) {
            return Optional.empty();
        }
        String from = topicToAddress(log.getTopics().get(1));
        String to = topicToAddress(log.getTopics().get(2));
        BigInteger raw = parseUint256Data(log.getData());
        BigDecimal human = toHumanAmount(raw, meta.decimals());
        return Optional.of(new DecodedTransfer(meta.symbol(), normalizeAddress(tokenAddr), from, to, raw, human));
    }

    /**
     * Scans all receipt logs and returns the largest core-token Transfer by human amount.
     */
    public static Optional<DecodedTransfer> findLargestCoreTokenTransfer(TransactionReceipt receipt) {
        if (receipt == null || receipt.getLogs() == null || receipt.getLogs().isEmpty()) {
            return Optional.empty();
        }
        DecodedTransfer best = null;
        for (Log log : receipt.getLogs()) {
            Optional<DecodedTransfer> d = decodeTransferEvent(log);
            if (d.isEmpty()) {
                continue;
            }
            DecodedTransfer t = d.get();
            if (best == null || t.humanAmount().compareTo(best.humanAmount()) > 0) {
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }

    public record DecodedTransfer(
            String symbol,
            String tokenAddress,
            String from,
            String to,
            BigInteger rawValue,
            BigDecimal humanAmount
    ) {
    }

    private static String topicToAddress(String topic32) {
        if (topic32 == null || topic32.isBlank()) {
            return "";
        }
        String hex = Numeric.cleanHexPrefix(topic32);
        if (hex.length() < 40) {
            return "";
        }
        return "0x" + hex.substring(hex.length() - 40).toLowerCase(Locale.ROOT);
    }

    private static BigInteger parseUint256Data(String data) {
        if (data == null || data.isBlank() || "0x".equalsIgnoreCase(data.trim())) {
            return BigInteger.ZERO;
        }
        try {
            return Numeric.toBigInt(data.trim());
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    private static BigDecimal toHumanAmount(BigInteger raw, int decimals) {
        if (raw == null || raw.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = new BigDecimal(raw);
        BigDecimal div = BigDecimal.TEN.pow(decimals);
        return base.divide(div, Math.min(decimals, 18), RoundingMode.DOWN);
    }

    private static String normalizeAddress(String addr) {
        return addr.trim().toLowerCase(Locale.ROOT);
    }
}
