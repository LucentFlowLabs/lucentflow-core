package com.lucentflow.indexer.transformer;

/**
 * Enterprise exception for data integrity violations in transaction processing.
 * 
 * <p>Implementation Details:
 * Thrown when critical data validation fails during transformation.
 * Prevents data corruption and hallucination in blockchain indexing.
 * Enforces strict data quality standards across the processing pipeline.
 * Virtual thread compatible through immutable exception design.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public class DataIntegrityException extends RuntimeException {
    
    public DataIntegrityException(String message) {
        super(message);
    }
    
    public DataIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
