package com.lucentflow;

import com.lucentflow.api.config.ApiTestConfig;
import com.lucentflow.analyzer.worker.WhaleAnalysisWorker;
import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import com.lucentflow.indexer.source.BaseBlockSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies application context loads with Basescan configuration on H2 test profile.
 *
 * @author ArchLucent
 * @since 1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ApiTestConfig.class)
@TestPropertySource(properties = {
        "lucentflow.basescan.api-key=test-key",
        "lucentflow.basescan.base-url=https://api.basescan.org/api",
        "spring.task.scheduling.enabled=false",
        "spring.main.lazy-initialization=true"
})
class BasescanConfigTest {

    @MockitoBean
    private BaseBlockSource baseBlockSource;

    @MockitoBean
    private PipelineOrchestrator pipelineOrchestrator;

    @MockitoBean
    private WhaleAnalysisWorker whaleAnalysisWorker;

    @Test
    void contextLoads() {
        // Success means configuration and datasource resolve on the test profile.
    }
}
