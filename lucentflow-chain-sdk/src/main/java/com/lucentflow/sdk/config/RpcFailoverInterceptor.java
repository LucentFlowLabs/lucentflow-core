package com.lucentflow.sdk.config;

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
public class RpcFailoverInterceptor implements Interceptor {

    private final RpcEndpointState endpointState;

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
                // Do NOT failover on 429. This is a rate limit signal, not an endpoint outage.
                if (code == 429) {
                    return response;
                }
                if (code >= 400) {
                    response.close();
                    endpointState.activateFailoverToBackup();
                    Request retry = rewriteRequest(chain.request());
                    return chain.proceed(retry);
                }
            }
            return response;
        } catch (IOException ex) {
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
