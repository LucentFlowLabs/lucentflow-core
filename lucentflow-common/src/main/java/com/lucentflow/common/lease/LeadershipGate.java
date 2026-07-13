package com.lucentflow.common.lease;

/**
 * Leadership probe for indexer/analyzer single-writer gating.
 *
 * @author ArchLucent
 * @since 1.2
 */
public interface LeadershipGate {

    /**
     * @return true when this process may write sync checkpoints / drain analysis work
     */
    boolean isLeader();
}
