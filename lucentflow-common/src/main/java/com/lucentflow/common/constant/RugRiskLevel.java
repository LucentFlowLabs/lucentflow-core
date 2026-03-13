package com.lucentflow.common.constant;

/**
 * Risk level assessment for contract creator funding sources.
 * Used in Anti-Rug Pull detection and Genesis Funding Source tracing.
 */
public enum RugRiskLevel {
    LOW("Low Risk - Known CEX/Institutional"),
    MEDIUM("Medium Risk - DeFi Bridge/Mixer"),
    HIGH("High Risk - Suspicious Platform"),
    CRITICAL("Critical Risk - Known Mixer/Privacy Tool"),
    UNKNOWN("Unknown Risk - Insufficient Data");

    private final String description;

    RugRiskLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
