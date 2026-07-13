package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.AlertRuleCacheService;
import com.lucentflow.api.dto.AlertRuleDTO;
import com.lucentflow.api.dto.AlertRuleUpsertRequest;
import com.lucentflow.common.entity.AlertRule;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.AlertRuleRepository;
import com.lucentflow.common.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-project alert rule CRUD with cache synchronization.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final ProjectRepository projectRepository;
    private final AlertRuleCacheService alertRuleCacheService;

    @Value("${lucentflow.alert.global-risk-threshold:70}")
    private int globalRiskThreshold;

    @Transactional(readOnly = true)
    public AlertRuleDTO getForProject(Long projectId) {
        requireProject(projectId);
        return alertRuleRepository.findByProjectId(projectId)
                .map(this::toDto)
                .orElseGet(() -> defaultDto(projectId));
    }

    @Transactional
    public AlertRuleDTO upsert(AlertRuleUpsertRequest request, Long projectId) {
        Project project = requireProject(projectId);
        validateRequest(request);

        AlertRule rule = alertRuleRepository.findByProjectId(projectId)
                .orElseGet(() -> AlertRule.builder().project(project).build());

        rule.setMinRiskScore(request.minRiskScore());
        rule.setWatchlistOnly(Boolean.TRUE.equals(request.watchlistOnly()));
        rule.setContractCreationOnly(Boolean.TRUE.equals(request.contractCreationOnly()));
        rule.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));

        AlertRule saved = alertRuleRepository.save(rule);
        alertRuleCacheService.refresh();
        return toDto(saved);
    }

    private AlertRuleDTO toDto(AlertRule rule) {
        return new AlertRuleDTO(
                rule.getId(),
                rule.getProject() == null ? null : rule.getProject().getId(),
                rule.getMinRiskScore(),
                rule.getWatchlistOnly(),
                rule.getContractCreationOnly(),
                rule.getEnabled(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private AlertRuleDTO defaultDto(Long projectId) {
        return new AlertRuleDTO(
                null,
                projectId,
                Math.max(0, globalRiskThreshold),
                false,
                false,
                true,
                null,
                null
        );
    }

    private void validateRequest(AlertRuleUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.minRiskScore() == null) {
            throw new IllegalArgumentException("minRiskScore is required");
        }
        if (request.minRiskScore() < 0 || request.minRiskScore() > 100) {
            throw new IllegalArgumentException("minRiskScore must be between 0 and 100");
        }
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project scope is required");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }
}
