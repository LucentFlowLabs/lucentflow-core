package com.lucentflow.common.exception;

/**
 * Enterprise-grade exception for cryptographic operation failures.
 * 
 * <p>Implementation Details:
 * Provides standardized error handling for cryptographic operations throughout
 * the application chain. Wraps underlying crypto library exceptions
 * with clear error messages and proper cause chaining.
 * Virtual thread compatible through immutable exception design.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public class CryptoException extends RuntimeException {
    
    public CryptoException(String message) {
        super(message);
    }
    
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CryptoException(Throwable cause) {
        super(cause);
    }
}
