package com.lucentflow.pipeline;

import java.util.concurrent.Callable;

/**
 * Runs a blocking JSON-RPC call under whatever concurrency policy the runtime has.
 * Indexer uses {@code BaseBlockSource} (governor + pacing). API-only uses a
 * pass-through so Genesis Trace does not require the scan stack or write {@code sync_status}.
 *
 * @author ArchLucent
 * @since 1.2
 */
public interface RpcPermitPort {

    /**
     * Execute {@code action} while holding the runtime RPC permit (or immediately when none).
     *
     * @param action RPC work
     * @param <T>    result type
     * @return action result
     */
    <T> T runWithRpcPermit(Callable<T> action);
}
