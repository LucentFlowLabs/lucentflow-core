package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;

/**
 * Unified contract for outbound alert channels.
 *
 * @author ArchLucent
 * @since 1.0
 */
public interface AlertProvider {

    void sendHighRiskAlertAsync(WhaleTransaction tx, AlertDispatchContext context);

    /**
     * {@code true} for per-project channels (HMAC webhooks): one send per dispatch context.
     * {@code false} for operator-global channels (Discord / Telegram): {@link AlertService}
     * sends once with {@link AlertDispatchContext#mergeForGlobalChannel}.
     */
    default boolean supportsProjectScopedDispatch() {
        return false;
    }
}
