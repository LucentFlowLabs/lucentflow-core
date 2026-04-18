package com.lucentflow.sdk.config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Rewrites JSON-RPC requests to {@link RpcEndpointState#currentRpcUrl()} and triggers backup failover
 * on HTTP failures or I/O failures (timeouts, connection resets).
 *
 * <p>IMPORTANT: HTTP 429 (rate limit) is not a "broken" endpoint. Switching to backup on 429
 * causes immediate flapping and defeats adaptive backpressure. 429 must be handled by
 * higher-layer cooldown logic (governor + scheduler pacing).</p>
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
public class RpcFailoverInterceptor implements Interceptor {

    private final RpcEndpointState endpointState;
    private static final long PEEK_BODY_BYTES = 8192L;

    public RpcFailoverInterceptor(RpcEndpointState endpointState) {
        this.endpointState = endpointState;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        endpointState.expireFailoverIfDue();
        Request request = rewriteRequest(chain.request());
        try {
            Response response = chain.proceed(request);
            if (!response.isSuccessful()) {
                int code = response.code();
                String bodySnippet = "";
                try {
                    bodySnippet = response.peekBody(PEEK_BODY_BYTES).string();
                } catch (IOException ignored) {
                    // Keep failover decision robust even if body cannot be read.
                }
                String lowerBody = bodySnippet.toLowerCase();

                // Chainstack plan-capability rejection: do NOT trigger global endpoint failover.
                // This is not transport outage; switching all traffic to backup is too aggressive.
                if (code == 403
                        && (lowerBody.contains("archive, debug and trace requests are not available")
                        || lowerBody.contains("not available on your current plan"))) {
                    log.warn("[FAILOVER-REASON] HTTP 403 plan capability restriction detected; keeping current endpoint (no global failover).");
                    return response;
                }

                // 4xx is usually quota/auth/request-shape, not endpoint outage.
                // Keep traffic on primary and let upper layers handle backpressure/retry semantics.
                if (code >= 400 && code < 500) {
                    return response;
                }
                if (code >= 500) {
                    log.warn("[FAILOVER-REASON] HTTP {} from RPC endpoint. Activating backup route.", code);
                    response.close();
                    endpointState.activateFailoverToBackup();
                    Request retry = rewriteRequest(chain.request());
                    return chain.proceed(retry);
                }
            }
            return response;
        } catch (IOException ex) {
            log.warn("[FAILOVER-REASON] I/O failure from RPC endpoint: {}. Activating backup route.",
                    ex.getClass().getSimpleName());
            endpointState.activateFailoverToBackup();
            Request retry = rewriteRequest(chain.request());
            return chain.proceed(retry);
        }
    }

    private Request rewriteRequest(Request original) {
        HttpUrl target = HttpUrl.parse(endpointState.currentRpcUrl());
        if (target == null) {
            return original;
        }
        return original.newBuilder().url(target).build();
    }
}
