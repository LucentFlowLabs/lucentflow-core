package com.lucentflow.analyzer.pipeline;

import com.lucentflow.common.entity.WhaleTransaction;

/**
 * Strategy interface for pluggable whale transaction analysis components.
 * 
 * <p>Implementation Details:
 * Defines contract for whale transaction analysis handlers in the plug-and-play
 * architecture. Each implementation provides specialized analysis functionality
 * including security heuristics, pattern recognition, and address classification.
 * Virtual thread compatible through stateless interface design.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public interface WhaleAnalysisHandler {
    
    /**
     * Processes whale transaction through specialized analysis logic.
     * 
     * <p>Each implementation provides unique analysis capabilities such as
     * security heuristics, pattern recognition, address classification, or
     * behavioral analysis. Implementations should be stateless and thread-safe.</p>
     * 
     * @param whaleTransaction Whale transaction entity containing complete transaction data
     * @throws Exception if analysis processing encounters errors
     * @throws IllegalArgumentException if whaleTransaction is null
     */
    void handle(WhaleTransaction whaleTransaction) throws Exception;
    
    /**
     * Returns the handler name for logging and monitoring purposes.
     * 
     * <p>Default implementation uses class simple name for consistent identification.
     * Can be overridden by implementations requiring custom naming conventions.</p>
     * 
     * @return Handler name suitable for logging and monitoring
     */
    default String getHandlerName() {
        return this.getClass().getSimpleName();
    }
}
