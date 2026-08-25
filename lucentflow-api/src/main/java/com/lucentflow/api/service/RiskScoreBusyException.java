package com.lucentflow.api.service;

/**
 * Point-lookup RPC permit pool is exhausted.
 *
 * @author ArchLucent
 * @since 1.2
 */
public class RiskScoreBusyException extends RuntimeException {

    public RiskScoreBusyException(String message) {
        super(message);
    }
}
