package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates high-risk alert fan-out to all configured providers.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final List<AlertProvider> alertProviders;
    private final WatchlistCacheService watchlistCacheService;
    private final AlertRuleCacheService alertRuleCacheService;

    @Value("${lucentflow.alert.global-risk-threshold:70}")
    private int globalRiskThreshold = 70;

    /**
     * Fire-and-forget to all channels; provider failures are isolated.
     */
    public void sendAlertIfNeeded(WhaleTransaction tx) {
        if (tx == null) {
            return;
        }

        List<AlertDispatchContext> contexts = buildDispatchContexts(tx);
        if (contexts.isEmpty()) {
            return;
        }

        for (AlertProvider alertProvider : alertProviders) {
            List<AlertDispatchContext> providerContexts = alertProvider.supportsProjectScopedDispatch()
                    ? contexts
                    : List.of(AlertDispatchContext.mergeForGlobalChannel(contexts));
            for (AlertDispatchContext context : providerContexts) {
                try {
                    alertProvider.sendHighRiskAlertAsync(tx, context);
                } catch (Exception e) {
                    log.warn("[ALERT] provider={} failed for tx={} err={}",
                            alertProvider.getClass().getSimpleName(),
                            tx.getHash(),
                            e.getMessage());
                }
            }
        }
    }

    private List<AlertDispatchContext> buildDispatchContexts(WhaleTransaction tx) {
        List<WatchlistCacheService.WatchlistHit> hits =
                watchlistCacheService.findHits(tx.getFromAddress(), tx.getToAddress());

        List<AlertDispatchContext> contexts = new ArrayList<>();
        Set<Long> dispatchedProjects = new HashSet<>();

        for (WatchlistCacheService.WatchlistHit hit : hits) {
            if (hit.projectId() != null && dispatchedProjects.contains(hit.projectId())) {
                continue;
            }
            AlertRuleCacheService.ProjectAlertRule rule = alertRuleCacheService.ruleForProject(hit.projectId());
            if (!alertRuleCacheService.shouldAlert(rule, tx, true)) {
                continue;
            }
            contexts.add(new AlertDispatchContext(
                    true,
                    hit.label(),
                    hit.category(),
                    hit.address(),
                    hit.projectId(),
                    resolveWebhookUrl(rule, hit.projectWebhookUrl()),
                    resolveWebhookSecret(rule, hit.projectWebhookSecret())));
            dispatchedProjects.add(hit.projectId());
        }

        for (AlertRuleCacheService.ProjectAlertRule rule : alertRuleCacheService.allRules()) {
            if (rule.projectId() == null || dispatchedProjects.contains(rule.projectId())) {
                continue;
            }
            if (!alertRuleCacheService.shouldAlert(rule, tx, false)) {
                continue;
            }
            contexts.add(new AlertDispatchContext(
                    false,
                    null,
                    null,
                    null,
                    rule.projectId(),
                    rule.projectWebhookUrl(),
                    rule.projectWebhookSecret()));
            dispatchedProjects.add(rule.projectId());
        }

        if (contexts.isEmpty() && meetsGlobalThreshold(tx)) {
            contexts.add(new AlertDispatchContext(false, null, null, null, null, null, null));
        }
        return contexts;
    }

    private boolean meetsGlobalThreshold(WhaleTransaction tx) {
        int riskScore = tx.getRiskScore() == null ? 0 : tx.getRiskScore();
        return riskScore >= Math.max(0, globalRiskThreshold);
    }

    private String resolveWebhookUrl(AlertRuleCacheService.ProjectAlertRule rule, String hitWebhookUrl) {
        if (rule != null && rule.projectWebhookUrl() != null && !rule.projectWebhookUrl().isBlank()) {
            return rule.projectWebhookUrl();
        }
        return hitWebhookUrl;
    }

    private String resolveWebhookSecret(AlertRuleCacheService.ProjectAlertRule rule, String hitWebhookSecret) {
        if (rule != null && rule.projectWebhookSecret() != null && !rule.projectWebhookSecret().isBlank()) {
            return rule.projectWebhookSecret();
        }
        return hitWebhookSecret;
    }
}
