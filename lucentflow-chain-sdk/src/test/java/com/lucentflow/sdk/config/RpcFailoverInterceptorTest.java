package com.lucentflow.sdk.config;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP 429 is pacing, not endpoint death: the interceptor must not switch to backup.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class RpcFailoverInterceptorTest {

    private static final String PRIMARY = "https://primary.example/rpc";
    private static final String BACKUP = "https://backup.example/rpc";

    @Mock
    private Interceptor.Chain chain;

    @Test
    void http429_doesNotActivateBackupFailover() throws IOException {
        RpcEndpointState state = new RpcEndpointState(PRIMARY, BACKUP);
        RpcFailoverInterceptor interceptor = new RpcFailoverInterceptor(state);
        Request original = new Request.Builder().url(PRIMARY).build();
        when(chain.request()).thenReturn(original);
        when(chain.proceed(any())).thenReturn(httpResponse(original, 429, "Too Many Requests"));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(429);
        assertThat(state.isFailoverActive()).isFalse();
        assertThat(state.currentRpcUrl()).isEqualTo(PRIMARY);
        verify(chain, times(1)).proceed(any());
    }

    @Test
    void http500_activatesBackupAndRetries() throws IOException {
        RpcEndpointState state = new RpcEndpointState(PRIMARY, BACKUP);
        RpcFailoverInterceptor interceptor = new RpcFailoverInterceptor(state);
        Request original = new Request.Builder().url(PRIMARY).build();
        when(chain.request()).thenReturn(original);
        when(chain.proceed(any())).thenReturn(
                httpResponse(original, 500, "Internal Server Error"),
                httpResponse(original, 200, "OK"));

        Response response = interceptor.intercept(chain);

        assertThat(response.code()).isEqualTo(200);
        assertThat(state.isFailoverActive()).isTrue();
        assertThat(state.currentRpcUrl()).isEqualTo(BACKUP);
        verify(chain, times(2)).proceed(any());
    }

    private static Response httpResponse(Request request, int code, String message) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(ResponseBody.create("{}", MediaType.parse("application/json")))
                .build();
    }
}
