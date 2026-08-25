package com.lucentflow.common.pipeline;

import org.web3j.protocol.core.methods.response.Transaction;

import java.time.Instant;
import java.util.Objects;

/**
 * Whale candidate on the ingest pipe, tagged with the producing block's timestamp.
 *
 * @author ArchLucent
 * @since 1.2
 */
public record PipedTransaction(Transaction transaction, Instant blockTimestamp) {

    public PipedTransaction {
        Objects.requireNonNull(transaction, "transaction");
        if (blockTimestamp == null) {
            blockTimestamp = Instant.now();
        }
    }
}
