package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

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

    @Value("${lucentflow.alert.global-risk-threshold:70}")
    private int globalRiskThreshold;

    /**
     * Fire-and-forget to all channels; provider failures are isolated.
     */
    public void sendAlertIfNeeded(WhaleTransaction tx) {
        if (tx == null) {
            return;
        }
        List<WatchlistCacheService.WatchlistHit> hits =
                watchlistCacheService.findHits(tx.getFromAddress(), tx.getToAddress());

        int riskScore = tx.getRiskScore() == null ? 0 : tx.getRiskScore();
        boolean isWatchlistHit = !hits.isEmpty();
        if (!isWatchlistHit && riskScore < Math.max(0, globalRiskThreshold)) {
            return;
        }
        List<AlertDispatchContext> contexts = new ArrayList<>();
        if (hits.isEmpty()) {
            contexts.add(new AlertDispatchContext(false, null, null, null, null, null));
        } else {
            for (WatchlistCacheService.WatchlistHit hit : hits) {
                contexts.add(new AlertDispatchContext(
                        true,
                        hit.label(),
                        hit.category(),
                        hit.address(),
                        hit.projectId(),
                        hit.projectWebhookUrl()));
            }
        }

        for (AlertProvider alertProvider : alertProviders) {
            List<AlertDispatchContext> providerContexts = alertProvider.supportsProjectScopedDispatch()
                    ? contexts
                    : List.of(contexts.get(0));
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
}
