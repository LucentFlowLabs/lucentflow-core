package com.lucentflow.api.dto;

/**
 * On-demand risk score request. Provide {@code address} and/or {@code txHash}.
 *
 * @author ArchLucent
 * @since 1.2
 */
public record RiskScoreRequest(String address, String txHash) {
}
