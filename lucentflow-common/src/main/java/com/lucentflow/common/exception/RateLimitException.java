package com.lucentflow.common.exception;

/**
 * Signals an upstream RPC rate limit (HTTP 429 / "over rate limit") and is used to trigger
 * adaptive backoff via retry policies.
 *
 * @author ArchLucent
 * @since 1.0
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
