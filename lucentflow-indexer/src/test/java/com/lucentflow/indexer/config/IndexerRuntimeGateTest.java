package com.lucentflow.indexer.config;

import com.lucentflow.indexer.control.AdaptiveBackpressureController;
import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import com.lucentflow.indexer.source.BaseBlockSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scan / checkpoint beans must stay behind {@link ConditionalOnIndexerEnabled}
 * so the API replica cannot write {@code sync_status} id=1.
 *
 * @author ArchLucent
 * @since 1.2
 */
class IndexerRuntimeGateTest {

    @Test
    void scanStackIsIndexerOnly() {
        assertTrue(BaseBlockSource.class.isAnnotationPresent(ConditionalOnIndexerEnabled.class));
        assertTrue(RpcConcurrencyGovernor.class.isAnnotationPresent(ConditionalOnIndexerEnabled.class));
        assertTrue(AdaptiveBackpressureController.class.isAnnotationPresent(ConditionalOnIndexerEnabled.class));
        assertTrue(IndexerRpcProfile.class.isAnnotationPresent(ConditionalOnIndexerEnabled.class));
        assertTrue(PipelineOrchestrator.class.isAnnotationPresent(ConditionalOnIndexerEnabled.class));
        assertTrue(com.lucentflow.indexer.source.DirectRpcPermitPort.class
                .isAnnotationPresent(ConditionalOnIndexerDisabled.class));
    }
}
