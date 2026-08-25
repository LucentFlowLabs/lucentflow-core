package com.lucentflow.api.service;

/**
 * Point-lookup exceeded {@code lucentflow.api.risk-score.timeout-ms}.
 *
 * @author ArchLucent
 * @since 1.2
 */
public class RiskScoreTimeoutException extends RuntimeException {

    public RiskScoreTimeoutException(String message) {
        super(message);
    }

    public RiskScoreTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
