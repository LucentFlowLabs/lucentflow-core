package com.lucentflow.sdk.config;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Rewrites JSON-RPC requests to {@link RpcEndpointState#currentRpcUrl()} and triggers backup failover
 * on HTTP 4xx/5xx or I/O failures (timeouts, connection resets).
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
