package com.lucentflow.api.health;

import com.lucentflow.api.config.ApiTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Decided probe policy: jsonRpc DOWN must not fail liveness or readiness HTTP status.
 * Kubernetes uses {@code /actuator/health/liveness} and {@code /actuator/health/readiness};
 * the aggregate {@code /actuator/health} stays HTTP 200 so operators can read RPC details.
 *
 * <p>The test profile points JSON-RPC at {@code localhost:8545}, which is down, so {@code jsonRpc}
 * is DOWN. Readiness must still be UP because the group is {@code db} only.</p>
 *
 * @author ArchLucent
 * @since 1.2
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestConfig.class)
@TestPropertySource(properties = {
        "lucentflow.runtime.enable-indexer=false",
        "lucentflow.runtime.enable-analyzer=false",
        "spring.task.scheduling.enabled=false",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.probes.enabled=true"
})
class HealthProbePolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aggregateHealthStaysHttp200WhenJsonRpcIsDown() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void readinessStaysUpWhenJsonRpcIsDown() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessStaysUpWhenJsonRpcIsDown() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void applicationYamlKeepsJsonRpcOutOfProbeGroups() throws Exception {
        Path moduleDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = moduleDir.getFileName().toString().equals("lucentflow-api")
                ? moduleDir.getParent()
                : moduleDir;
        String yaml = Files.readString(repoRoot
                .resolve("lucentflow-api")
                .resolve("src/main/resources/application.yml"));
        assertThat(yaml).contains("probes:");
        assertThat(yaml).contains("include: livenessState");
        assertThat(yaml).contains("include: readinessState,db");
        assertThat(yaml).doesNotContain("include: readinessState,db,jsonRpc");
    }
}
