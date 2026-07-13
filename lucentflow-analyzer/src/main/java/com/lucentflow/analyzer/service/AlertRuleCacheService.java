package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.AlertRule;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.AlertRuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of per-project alert rules for O(1) pipeline evaluation.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleCacheService {

    private final AlertRuleRepository alertRuleRepository;

    @Value("${lucentflow.alert.global-risk-threshold:70}")
    private int globalRiskThreshold;

    private final ConcurrentHashMap<Long, ProjectAlertRule> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Transactional(readOnly = true)
    public synchronized void refresh() {
        Map<Long, ProjectAlertRule> latest = new ConcurrentHashMap<>();
        for (AlertRule rule : alertRuleRepository.findAll()) {
            if (rule.getProject() == null || rule.getProject().getId() == null) {
                continue;
            }
            // Inactive projects must not receive pipeline alerts.
            if (!Boolean.TRUE.equals(rule.getProject().getIsActive())) {
                continue;
            }
            latest.put(rule.getProject().getId(), toSnapshot(rule));
        }
        cache.clear();
        cache.putAll(latest);
        log.info("[ALERT-RULE] Cache refreshed: {} active project rules loaded", cache.size());
    }

    public Collection<ProjectAlertRule> allRules() {
        return cache.values();
    }

    public ProjectAlertRule ruleForProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        ProjectAlertRule cached = cache.get(projectId);
        if (cached != null) {
            return cached;
        }
        return defaultRule(projectId, null);
    }

    public boolean shouldAlert(ProjectAlertRule rule, WhaleTransaction tx, boolean watchlistHit) {
        if (tx == null) {
            return false;
        }
        ProjectAlertRule effective = rule == null
                ? defaultRule(null, null)
                : rule;
        if (!effective.enabled()) {
            return false;
        }
        if (effective.contractCreationOnly() && !Boolean.TRUE.equals(tx.getIsContractCreation())) {
            return false;
        }
        if (watchlistHit) {
            return true;
        }
        if (effective.watchlistOnly()) {
            return false;
        }
        int score = tx.getRiskScore() == null ? 0 : tx.getRiskScore();
        return score >= effective.minRiskScore();
    }

    private ProjectAlertRule toSnapshot(AlertRule rule) {
        return new ProjectAlertRule(
                rule.getProject().getId(),
                rule.getProject().getWebhookUrl(),
                rule.getMinRiskScore(),
                Boolean.TRUE.equals(rule.getWatchlistOnly()),
                Boolean.TRUE.equals(rule.getContractCreationOnly()),
                Boolean.TRUE.equals(rule.getEnabled())
        );
    }

    private ProjectAlertRule defaultRule(Long projectId, String webhookUrl) {
        return new ProjectAlertRule(
                projectId,
                webhookUrl,
                Math.max(0, globalRiskThreshold),
                false,
                false,
                true
        );
    }

    public record ProjectAlertRule(
            Long projectId,
            String projectWebhookUrl,
            int minRiskScore,
            boolean watchlistOnly,
            boolean contractCreationOnly,
            boolean enabled
    ) {
    }
}
