package com.lucentflow;

import com.lucentflow.api.config.ApiTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
        "lucentflow.runtime.enable-indexer=false",
        "lucentflow.runtime.enable-analyzer=false",
        "spring.task.scheduling.enabled=false"
})
class BasescanConfigTest {

    @Test
    void contextLoads() {
        // Success means configuration and datasource resolve on the test profile.
    }
}
