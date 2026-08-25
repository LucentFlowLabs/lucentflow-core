package com.lucentflow.api.controller;

import com.lucentflow.api.dto.AlertRuleDTO;
import com.lucentflow.api.dto.AlertRuleUpsertRequest;
import com.lucentflow.api.security.ProjectContext;
import com.lucentflow.api.service.AlertRuleService;
import com.lucentflow.common.entity.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract for per-project alert-rule GET/PUT.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleControllerTest {

    @Mock
    private AlertRuleService alertRuleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlertRuleController(alertRuleService)).build();
    }

    @AfterEach
    void clearContext() {
        ProjectContext.clear();
    }

    @Test
    void put_withoutProjectContext_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minRiskScore\":80}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutProjectContext_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/alert-rules"))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_upsertsCurrentProjectRule() throws Exception {
        ProjectContext.set(Project.builder().id(7L).name("desk").build());
        when(alertRuleService.upsert(any(AlertRuleUpsertRequest.class), eq(7L)))
                .thenReturn(new AlertRuleDTO(
                        3L, 7L, 80, false, false, true,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(put("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minRiskScore\":80,\"watchlistOnly\":false,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.projectId").value(7))
                .andExpect(jsonPath("$.minRiskScore").value(80));

        verify(alertRuleService).upsert(any(AlertRuleUpsertRequest.class), eq(7L));
    }

    @Test
    void put_invalidScore_returns400() throws Exception {
        ProjectContext.set(Project.builder().id(7L).name("desk").build());
        when(alertRuleService.upsert(any(AlertRuleUpsertRequest.class), eq(7L)))
                .thenThrow(new IllegalArgumentException("minRiskScore must be between 0 and 100"));

        mockMvc.perform(put("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minRiskScore\":999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_returnsCurrentRule() throws Exception {
        ProjectContext.set(Project.builder().id(7L).name("desk").build());
        when(alertRuleService.getForProject(7L))
                .thenReturn(new AlertRuleDTO(null, 7L, 70, false, false, true, null, null));

        mockMvc.perform(get("/api/v1/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(7))
                .andExpect(jsonPath("$.minRiskScore").value(70));
    }
}
