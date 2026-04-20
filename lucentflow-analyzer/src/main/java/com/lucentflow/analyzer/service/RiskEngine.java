package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
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
 * @author ArchLucent
 * @since 1.0
 */
@Service
public class RiskEngine {

    public record RiskAssessment(int rawScore, Map<String, Integer> reasons) {}

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
        if (input != null && (input.startsWith("0x715018a6") || input.contains("715018a6"))) {
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

    private int addReason(Map<String, Integer> reasons, String key, int points) {
        if (points <= 0) {
            return 0;
        }
        Integer existing = reasons.get(key);
        reasons.put(key, (existing == null ? 0 : existing) + points);
        return points;
    }
}
