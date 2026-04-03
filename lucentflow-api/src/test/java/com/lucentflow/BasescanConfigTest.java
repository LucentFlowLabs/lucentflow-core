package com.lucentflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.flywaydb.core.Flyway;

/**
 * Test to verify application context loads successfully with Basescan configuration.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "lucentflow.basescan.api-key=test-key",
    "lucentflow.basescan.base-url=https://api.basescan.org/api"
})
public class BasescanConfigTest {
    
    @MockitoBean
    private Flyway flyway;
    
    @Test
    void contextLoads() {
        // This test will fail if PlaceholderResolutionException occurs
        // Success means configuration is properly resolved
    }
}
