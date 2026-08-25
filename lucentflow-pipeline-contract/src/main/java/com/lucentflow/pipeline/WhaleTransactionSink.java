package com.lucentflow.pipeline;

import com.lucentflow.common.entity.WhaleTransaction;

import java.util.List;

/**
 * Persistence sink for enriched whale transactions.
 *
 * @author ArchLucent
 * @since 1.2
 */
public interface WhaleTransactionSink {

    /**
     * Persist a whale batch. Implementations must propagate persistence failures
     * (never swallow them) so callers can skip alerting on uncommitted rows.
     */
    void saveWhaleTransactions(List<WhaleTransaction> transactions);
}
