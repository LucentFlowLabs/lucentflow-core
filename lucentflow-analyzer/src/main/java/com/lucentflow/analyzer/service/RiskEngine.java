package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigInteger;

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

    /**
     * Immutable record representing the computed risk assessment.
     * 
     * @param score The computed risk score from 0 to 100
     * @param reasons The aggregated reasons for the applied risk score
     */
    public record RiskAssessment(int score, String reasons) {}

    /**
     * Calculates an institutional-grade risk score (0-100) based on multiple heuristic factors.
     * Evaluates funding origins, gas fee strategies, address freshness, and specific contract interactions.
     * 
     * @param whaleTx The enriched whale transaction containing pre-computed risk levels
     * @param tx The raw Web3j transaction containing execution parameters (gas, input data)
     * @return A {@link RiskAssessment} containing the final bounded score and descriptive reasons
     */
    public RiskAssessment calculateRisk(WhaleTransaction whaleTx, Transaction tx) {
        int score = 0;
        StringBuilder reasons = new StringBuilder();

        // 1. Funding Source Analysis (CEX vs. Anonymous Mixers)
        String riskLevel = whaleTx.getRugRiskLevel() != null ? whaleTx.getRugRiskLevel() : "UNKNOWN";
        int fundingScore = switch (riskLevel) {
            case "CRITICAL" -> 40;
            case "HIGH" -> 30;
            case "MEDIUM" -> 15;
            case "LOW" -> 0;
            default -> 10;
        };
        score += fundingScore;
        if (fundingScore > 0) {
            reasons.append("Funding Source Risk (").append(riskLevel).append("); ");
        }

        // 2. Gas Priority Fee Anomalies (Potential exit liquidity indicators)
        BigInteger priorityFee = tx.getMaxPriorityFeePerGas();
        if (priorityFee != null) {
            // EIP-1559 Transactions
            // A priority fee > 10 Gwei (10,000,000,000 wei) is highly anomalous for L2 networks like Base
            if (priorityFee.compareTo(BigInteger.valueOf(10_000_000_000L)) > 0) {
                score += 30;
                reasons.append("High Priority Fee (Exit Sign); ");
            } else if (priorityFee.compareTo(BigInteger.valueOf(2_000_000_000L)) > 0) {
                score += 15;
                reasons.append("Elevated Priority Fee; ");
            }
        } else if (tx.getGasPrice() != null && tx.getGasPrice().compareTo(BigInteger.valueOf(50_000_000_000L)) > 0) {
            // Legacy Transaction Fallback
            score += 20;
            reasons.append("High Gas Price Anomaly; ");
        }

        // 3. Contract Age and Address Freshness Factor
        if (Boolean.TRUE.equals(whaleTx.getIsContractCreation())) {
            score += 30;
            reasons.append("Fresh Contract Creation; ");
        } else if (tx.getNonce() != null && tx.getNonce().compareTo(BigInteger.valueOf(10)) < 0) {
            score += 20;
            reasons.append("Low Nonce (Freshness Factor); ");
        }

        // 4. Dangerous Method Signature Detection (e.g., renounceOwnership)
        String input = tx.getInput();
        if (input != null && (input.startsWith("0x715018a6") || input.contains("715018a6"))) {
            score += 20;
            reasons.append("Renounce Ownership Detected; ");
        }

        // Bound final score to maximum of 100
        score = Math.min(score, 100);
        
        // Format final reasons string, trimming the trailing semicolon and space
        String finalReasons = reasons.length() > 0 ? reasons.substring(0, reasons.length() - 2) : "Normal";
        
        return new RiskAssessment(score, finalReasons);
    }
}
