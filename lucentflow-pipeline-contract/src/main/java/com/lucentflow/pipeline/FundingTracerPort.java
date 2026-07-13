package com.lucentflow.pipeline;

import com.lucentflow.common.entity.WhaleTransaction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Genesis Trace / Anti-Rug funding enrichment port.
 *
 * @author ArchLucent
 * @since 1.2
 */
public interface FundingTracerPort {

    CompletableFuture<Optional<GenesisTraceOutcome>> traceOriginAsync(String address);

    CompletableFuture<WhaleTransaction> enrichRugMetrics(WhaleTransaction entity);
}
