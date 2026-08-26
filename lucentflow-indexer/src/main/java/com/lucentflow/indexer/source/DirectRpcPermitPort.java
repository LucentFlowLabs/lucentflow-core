package com.lucentflow.indexer.source;

import com.lucentflow.indexer.config.ConditionalOnIndexerDisabled;
import com.lucentflow.pipeline.RpcPermitPort;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * Pass-through RPC permit for API-only (and any process with indexer disabled).
 * Does not touch {@code sync_status} and does not share {@code RpcConcurrencyGovernor}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Component
@ConditionalOnIndexerDisabled
public class DirectRpcPermitPort implements RpcPermitPort {

    @Override
    public <T> T runWithRpcPermit(Callable<T> action) {
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("RPC call failed", e);
        }
    }
}
