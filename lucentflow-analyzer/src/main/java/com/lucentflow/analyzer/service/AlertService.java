package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * Fire-and-forget to all channels; provider failures are isolated.
     */
    public void sendHighRiskAlertAsync(WhaleTransaction tx) {
        if (tx == null) {
            return;
        }
        for (AlertProvider alertProvider : alertProviders) {
            try {
                alertProvider.sendHighRiskAlertAsync(tx);
            } catch (Exception e) {
                log.warn("[ALERT] provider={} failed for tx={} err={}",
                        alertProvider.getClass().getSimpleName(),
                        tx.getHash(),
                        e.getMessage());
            }
        }
    }
}
