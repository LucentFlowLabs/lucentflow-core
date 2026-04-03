package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.EntityTagCategory;
import com.lucentflow.common.entity.WhaleTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Automated discovery of candidate {@link com.lucentflow.common.entity.EntityTag} rows from whale signals
 * (high-value low-risk wallets, CEX-funded deployers).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagInferenceEngine {

    private static final BigDecimal HIGH_VALUE_ETH = new BigDecimal("500");
    private static final int MAX_LOW_RISK_SCORE = 19;
    private static final String INSTITUTION_CANDIDATE = "Potential Institution/Giga Whale";

    private final TagOracleService tagOracleService;

    /**
     * Persists inferred tags when rules match; safe to call on every whale before batch save.
     */
    public void inferCandidateTags(WhaleTransaction whaleTx) {
        if (whaleTx == null) {
            return;
        }
        maybeTagInstitution(whaleTx.getFromAddress(), whaleTx.getRiskScore(), whaleTx.getValueEth());
        maybeTagInstitution(whaleTx.getToAddress(), whaleTx.getRiskScore(), whaleTx.getValueEth());
        maybeTagCexUser(whaleTx);
    }

    private void maybeTagInstitution(String address, Integer riskScore, BigDecimal valueEth) {
        if (address == null || address.isBlank()) {
            return;
        }
        if (tagOracleService.resolveTag(address) != null) {
            return;
        }
        if (riskScore == null || riskScore > MAX_LOW_RISK_SCORE) {
            return;
        }
        if (valueEth == null || valueEth.compareTo(HIGH_VALUE_ETH) <= 0) {
            return;
        }
        tagOracleService.updateTag(address, INSTITUTION_CANDIDATE, EntityTagCategory.WHALE.name());
        log.info("[LUCENT-TAG] Inferred institution candidate for {}", address);
    }

    private void maybeTagCexUser(WhaleTransaction whaleTx) {
        String funding = whaleTx.getFundingSourceTag();
        if (!impliesCexFunding(funding)) {
            return;
        }
        String from = whaleTx.getFromAddress();
        if (from == null || from.isBlank()) {
            return;
        }
        if (tagOracleService.resolveTag(from) != null) {
            return;
        }
        String cex = cexBrandFromFundingTag(funding);
        tagOracleService.updateTag(from, cex + " User", EntityTagCategory.EXCHANGE.name());
        log.info("[LUCENT-TAG] Inferred CEX user label from funding tag '{}' for {}", funding, from);
    }

    private static boolean impliesCexFunding(String fundingTag) {
        if (fundingTag == null || fundingTag.isBlank()) {
            return false;
        }
        String u = fundingTag.toUpperCase(Locale.ROOT);
        if (u.contains("MIXER") || u.contains("BLACKLIST") || u.contains("TORNADO")) {
            return false;
        }
        if ("CEX_FUNDING".equals(u)) {
            return true;
        }
        return u.contains("COINBASE")
                || u.contains("BINANCE")
                || u.contains("KRAKEN")
                || u.contains("OKX")
                || u.contains("BYBIT")
                || u.contains("CRYPTO.COM")
                || u.contains("GEMINI")
                || (u.contains("HOT WALLET") && (u.contains("COINBASE") || u.contains("BINANCE")));
    }

    private static String cexBrandFromFundingTag(String fundingTag) {
        String u = fundingTag.toUpperCase(Locale.ROOT);
        if ("CEX_FUNDING".equals(u)) {
            return "CEX";
        }
        if (u.contains("COINBASE")) {
            return "Coinbase";
        }
        if (u.contains("BINANCE")) {
            return "Binance";
        }
        if (u.contains("KRAKEN")) {
            return "Kraken";
        }
        if (u.contains("OKX")) {
            return "OKX";
        }
        if (u.contains("BYBIT")) {
            return "Bybit";
        }
        if (u.contains("CRYPTO.COM")) {
            return "Crypto.com";
        }
        if (u.contains("GEMINI")) {
            return "Gemini";
        }
        return "CEX";
    }
}
