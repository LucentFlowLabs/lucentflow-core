package com.lucentflow.pipeline;

import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Read-side block / receipt access used by the analyzer.
 *
 * @author ArchLucent
 * @since 1.2
 */
public interface BlockSourcePort {

    long getLastScannedBlock();

    long getLatestBlockNumber();

    CompletableFuture<Optional<TransactionReceipt>> fetchTransactionReceiptAsync(String txHash);
}
