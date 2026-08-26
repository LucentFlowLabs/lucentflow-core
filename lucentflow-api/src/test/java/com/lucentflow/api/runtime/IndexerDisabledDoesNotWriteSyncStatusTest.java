package com.lucentflow.api.runtime;

import com.lucentflow.api.config.ApiTestConfig;
import com.lucentflow.common.repository.SyncStatusRepository;
import com.lucentflow.indexer.config.IndexerRpcProfile;
import com.lucentflow.indexer.config.RpcConcurrencyGovernor;
import com.lucentflow.indexer.control.AdaptiveBackpressureController;
import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import com.lucentflow.indexer.source.BaseBlockSource;
import com.lucentflow.indexer.source.DirectRpcPermitPort;
import com.lucentflow.pipeline.FundingTracerPort;
import com.lucentflow.pipeline.RpcPermitPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API process ({@code enable-indexer=false}) must not create the scan stack or
 * write {@code sync_status} id=1. Genesis Trace still binds via {@link DirectRpcPermitPort}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ApiTestConfig.class)
@TestPropertySource(properties = {
        "lucentflow.runtime.enable-indexer=false",
        "lucentflow.runtime.enable-analyzer=false",
        "spring.task.scheduling.enabled=false"
})
class IndexerDisabledDoesNotWriteSyncStatusTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SyncStatusRepository syncStatusRepository;

    @Test
    void scanBeansAreAbsentAndPermitIsPassThrough() {
        assertThat(applicationContext.getBeanProvider(BaseBlockSource.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBeanProvider(RpcConcurrencyGovernor.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBeanProvider(AdaptiveBackpressureController.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBeanProvider(IndexerRpcProfile.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBeanProvider(PipelineOrchestrator.class).getIfAvailable()).isNull();

        RpcPermitPort permit = applicationContext.getBean(RpcPermitPort.class);
        assertThat(permit).isInstanceOf(DirectRpcPermitPort.class);
        assertThat(applicationContext.getBean(FundingTracerPort.class)).isNotNull();
    }

    @Test
    void hibernateSchemaDoesNotGainACheckpointRowFromApiStartup() {
        assertThat(syncStatusRepository.findById(1L))
                .as("API startup must not upsert sync_status id=1")
                .isEmpty();
    }
}
