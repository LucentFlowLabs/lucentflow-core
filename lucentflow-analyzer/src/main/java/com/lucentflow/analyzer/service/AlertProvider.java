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

    default boolean supportsProjectScopedDispatch() {
        return false;
    }
}
