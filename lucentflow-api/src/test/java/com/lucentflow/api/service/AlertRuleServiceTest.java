package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.AlertRuleCacheService;
import com.lucentflow.api.dto.AlertRuleDTO;
import com.lucentflow.api.dto.AlertRuleUpsertRequest;
import com.lucentflow.common.entity.AlertRule;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.AlertRuleRepository;
import com.lucentflow.common.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alert-rule upsert validates score bounds and hot-reloads the analyzer cache.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AlertRuleCacheService alertRuleCacheService;

    @InjectMocks
    private AlertRuleService alertRuleService;

    @Test
    void upsert_rejectsScoreOutsideZeroToOneHundred() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(Project.builder().id(1L).build()));

        assertThatThrownBy(() -> alertRuleService.upsert(
                new AlertRuleUpsertRequest(101, false, false, true), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minRiskScore");
    }

    @Test
    void upsert_savesAndRefreshesCache() {
        Project project = Project.builder().id(1L).name("builder").build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(alertRuleRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(alertRuleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> {
            AlertRule saved = invocation.getArgument(0);
            saved.setId(9L);
            saved.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return saved;
        });

        AlertRuleDTO dto = alertRuleService.upsert(
                new AlertRuleUpsertRequest(80, true, false, true), 1L);

        assertThat(dto.id()).isEqualTo(9L);
        assertThat(dto.minRiskScore()).isEqualTo(80);
        assertThat(dto.watchlistOnly()).isTrue();
        verify(alertRuleCacheService).refresh();
    }
}
