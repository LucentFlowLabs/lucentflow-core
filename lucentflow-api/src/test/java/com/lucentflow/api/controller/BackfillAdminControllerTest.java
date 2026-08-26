package com.lucentflow.api.controller;

import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backfill is served by the indexer process; API-only replicas return 503.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class BackfillAdminControllerTest {

    @Mock
    private ObjectProvider<PipelineOrchestrator> pipelineOrchestrator;

    @Mock
    private PipelineOrchestrator orchestrator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BackfillAdminController(pipelineOrchestrator)).build();
    }

    @Test
    void post_withoutIndexer_returns503WithWorkerHint() throws Exception {
        when(pipelineOrchestrator.getIfAvailable()).thenReturn(null);

        mockMvc.perform(post("/api/v1/admin/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBlock\":1,\"toBlock\":2}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(
                        "Indexer runtime disabled (lucentflow.runtime.enable-indexer=false)"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("port-forward")));
    }

    @Test
    void post_withIndexer_returnsAcceptedReport() throws Exception {
        when(pipelineOrchestrator.getIfAvailable()).thenReturn(orchestrator);
        when(orchestrator.backfillHistoricalRange(10L, 12L))
                .thenReturn(new PipelineOrchestrator.BackfillReport(10L, 12L, 3L, true));

        mockMvc.perform(post("/api/v1/admin/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBlock\":10,\"toBlock\":12}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fromBlock").value(10))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void post_missingRange_returns400() throws Exception {
        when(pipelineOrchestrator.getIfAvailable()).thenReturn(orchestrator);

        mockMvc.perform(post("/api/v1/admin/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
