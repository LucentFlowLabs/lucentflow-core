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

    void saveWhaleTransactions(List<WhaleTransaction> transactions);
}
