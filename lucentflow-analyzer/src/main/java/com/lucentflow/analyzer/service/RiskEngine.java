package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.pipeline.WhaleIngressFilter;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core engine for evaluating the risk profile of whale transactions and contract deployments.
 * Calculates institutional-grade risk scores using multi-factor heuristics including
 * funding sources, gas anomalies, contract freshness, and dangerous method signatures.
 *
 * <p>Ingest ({@code WhaleAnalysisWorker}) and {@code POST /api/v1/risk/score} both finish
 * through {@link #complete(RiskAssessment, boolean, boolean)} so revert and blacklist
 * weights cannot drift. Fetch policy still differs: ingest may skip receipt / genesis;
 * the point-lookup API always attempts both.</p>
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
public class RiskEngine {

    public static final String MODEL_VERSION = "risk-1.0";

    public static final String REASON_REVERT_PROBE = "REVERT_PROBE";
    public static final String REASON_BLACKLISTED_FUNDING = "BLACKLISTED_FUNDING_SOURCE";
    public static final int REVERT_PROBE_POINTS = 40;
    public static final int BLACKLISTED_FUNDING_POINTS = 35;

    /**
     * Ingest runs Genesis Trace when engine+revert raw score is strictly above this value.
     * Point-lookup API ignores the gate and always traces.
     */
    public static final int GENESIS_TRACE_MIN_SCORE = 40;

    public record RiskAssessment(int rawScore, Map<String, Integer> reasons) {}

    /**
     * Finished score after engine heuristics plus optional revert / blacklisted-funding weights.
     *
     * @param rawScore uncapped sum
     * @param score    {@link #clampScore(int)} of {@code rawScore}
     * @param reasons  reason code to points (never null, never empty)
     */
    public record CompletedScore(int rawScore, int score, Map<String, Integer> reasons) {}

    /**
     * Calculates an institutional-grade risk score (0-100) based on multiple heuristic factors.
     * Evaluates funding origins, gas fee strategies, address freshness, and specific contract interactions.
     * 
     * @param whaleTx The enriched whale transaction containing pre-computed risk levels
     * @param tx The raw Web3j transaction containing execution parameters (gas, input data)
     * @param recentDeploymentCount contract creations from the same {@code from} in the lookback window (e.g. 10 minutes)
     * @param identicalBytecodeCount prior rows in DB with the same creation bytecode hash in the lookback window (e.g. 7 days)
     * @return A {@link RiskAssessment} containing raw score and structured reason weights
     */
    public RiskAssessment calculateRisk(WhaleTransaction whaleTx, Transaction tx, int recentDeploymentCount,
                                        int identicalBytecodeCount) {
        int score = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();

        // 1. Funding Source Analysis (CEX vs. Anonymous Mixers)
        String riskLevel = whaleTx.getRugRiskLevel() != null ? whaleTx.getRugRiskLevel() : "LOW";
        int fundingScore = switch (riskLevel) {
            case "CRITICAL" -> 40;
            case "HIGH" -> 30;
            case "MEDIUM" -> 15;
            case "LOW" -> 0;
            default -> 10;
        };
        score += addReason(reasons, "FUNDING_" + riskLevel, fundingScore);

        // 2. Gas Priority Fee Anomalies (Potential exit liquidity indicators)
        BigInteger priorityFee = tx != null ? tx.getMaxPriorityFeePerGas() : null;
        BigInteger gasPrice = tx != null ? tx.getGasPrice() : null;
        if (priorityFee != null) {
            // EIP-1559 Transactions
            // A priority fee > 10 Gwei (10,000,000,000 wei) is highly anomalous for L2 networks like Base
            if (priorityFee.compareTo(BigInteger.valueOf(10_000_000_000L)) > 0) {
                score += addReason(reasons, "HIGH_PRIORITY_FEE", 30);
            } else if (priorityFee.compareTo(BigInteger.valueOf(2_000_000_000L)) > 0) {
                score += addReason(reasons, "ELEVATED_PRIORITY_FEE", 15);
            }
        } else if (gasPrice != null && gasPrice.compareTo(BigInteger.valueOf(50_000_000_000L)) > 0) {
            // Legacy Transaction Fallback
            score += addReason(reasons, "HIGH_GAS_PRICE_ANOMALY", 20);
        }

        // 3. Contract Age and Address Freshness Factor
        if (Boolean.TRUE.equals(whaleTx.getIsContractCreation())) {
            score += addReason(reasons, "CONTRACT_CREATION", 30);
        } else {
            BigInteger nonce = tx != null ? tx.getNonce() : null;
            if (nonce != null && nonce.compareTo(BigInteger.valueOf(10)) < 0) {
                score += addReason(reasons, "LOW_NONCE", 20);
            }
        }

        // 4. Dangerous Method Signature Detection (e.g., renounceOwnership)
        String input = tx != null ? tx.getInput() : null;
        if (WhaleIngressFilter.isRenounceOwnership(input)) {
            score += addReason(reasons, "RENOUNCE_OWNERSHIP", 20);
        }

        // 5. Serial deployer / contract factory pattern (multiple creations from same EOA in a short window)
        if (recentDeploymentCount > 2) {
            score += addReason(reasons, "SERIAL_DEPLOYER", 25);
        }

        // 6. Identical creation bytecode (clone / scam factory reuse)
        if (identicalBytecodeCount > 0) {
            score += addReason(reasons, "CONTRACT_CLONE", 20);
        }

        return new RiskAssessment(score, reasons);
    }

    /**
     * Raw engine score plus revert probe — used as the ingest Genesis Trace gate.
     *
     * @param base     engine assessment, may be {@code null}
     * @param reverted {@code true} when execution status is {@code REVERTED}
     * @return uncapped score after optional revert points
     */
    public static int rawAfterRevert(RiskAssessment base, boolean reverted) {
        int raw = base == null ? 0 : base.rawScore();
        return reverted ? raw + REVERT_PROBE_POINTS : raw;
    }

    /**
     * @param rawAfterEngineAndRevert result of {@link #rawAfterRevert(RiskAssessment, boolean)}
     * @return {@code true} when ingest should run Genesis Trace
     */
    public static boolean shouldTraceGenesis(int rawAfterEngineAndRevert) {
        return rawAfterEngineAndRevert > GENESIS_TRACE_MIN_SCORE;
    }

    /**
     * Shared finish step for ingest persist and the Risk Score API.
     * Order: engine reasons, then blacklisted funding, then revert probe, then clamp.
     *
     * @param base                 engine assessment, may be {@code null}
     * @param reverted             {@code true} when execution status is {@code REVERTED}
     * @param blacklistedFunding   {@code true} when genesis origin is blacklisted
     * @return raw, clamped score, and reasons
     */
    public CompletedScore complete(RiskAssessment base, boolean reverted, boolean blacklistedFunding) {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        int raw = 0;
        if (base != null) {
            raw = base.rawScore();
            if (base.reasons() != null) {
                reasons.putAll(base.reasons());
            }
        }
        if (blacklistedFunding) {
            raw += addReason(reasons, REASON_BLACKLISTED_FUNDING, BLACKLISTED_FUNDING_POINTS);
        }
        if (reverted) {
            raw += addReason(reasons, REASON_REVERT_PROBE, REVERT_PROBE_POINTS);
        }
        if (reasons.isEmpty()) {
            reasons.put("BASELINE_NORMAL", 0);
        }
        return new CompletedScore(raw, clampScore(raw), Map.copyOf(reasons));
    }

    /**
     * Product contract: persisted and API scores are always in {@code [0, 100]}.
     */
    public static int clampScore(int rawScore) {
        return Math.max(0, Math.min(100, rawScore));
    }

    private int addReason(Map<String, Integer> reasons, String key, int points) {
        if (points <= 0) {
            return 0;
        }
        Integer existing = reasons.get(key);
        reasons.put(key, (existing == null ? 0 : existing) + points);
        return points;
    }
}
