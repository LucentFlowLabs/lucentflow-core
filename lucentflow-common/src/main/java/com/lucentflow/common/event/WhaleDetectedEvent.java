package com.lucentflow.common.event;

import com.lucentflow.common.entity.WhaleTransaction;

/**
 * Spring ApplicationEvent for whale transaction detection notifications.
 * 
 * <p>Implementation Details:
 * Immutable record representing whale transaction detection events published
 * after successful database persistence. Enables event-driven architecture for
 * real-time whale analysis and monitoring. Virtual thread compatible through
 * immutable record design and stateless event propagation.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
public record WhaleDetectedEvent(WhaleTransaction whaleTransaction, long blockNumber) {
    
    /**
     * Canonical constructor for WhaleDetectedEvent record.
     * 
     * <p>Creates an immutable event instance containing the whale transaction
     * and the block number where it was detected. Validated by record compiler.</p>
     * 
     * @param whaleTransaction Whale transaction entity containing complete transaction data
     * @param blockNumber Block number where the transaction was detected
     * @throws IllegalArgumentException if parameters are null
     */
    public WhaleDetectedEvent {
        // Record constructor - automatically generated
    }
    
    /**
     * Returns string representation of the whale detection event.
     * 
     * <p>Includes whale transaction summary and block number for
     * debugging and logging purposes. Format optimized for readability.</p>
     * 
     * @return String representation containing event state
     */
    @Override
    public String toString() {
        return "WhaleDetectedEvent{" +
                "whaleTransaction=" + whaleTransaction +
                ", blockNumber=" + blockNumber +
                '}';
    }
}
