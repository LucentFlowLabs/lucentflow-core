package com.lucentflow.api.security;

import com.lucentflow.common.ratelimit.SharedRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for public platform IP soft rate limiting.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class PublicApiRateLimitInterceptorTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private SharedRateLimitService sharedRateLimitService;

    @Test
    void preHandle_throttlesSameIp() throws Exception {
        PublicApiRateLimitInterceptor interceptor =
                new PublicApiRateLimitInterceptor(sharedRateLimitService, 2);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(sharedRateLimitService.tryAcquire(eq("ip:203.0.113.10"), eq(2)))
                .thenReturn(true, true, false);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(eq(429), anyString());
    }

    @Test
    void preHandle_disabledWhenLimitZero() throws Exception {
        PublicApiRateLimitInterceptor interceptor =
                new PublicApiRateLimitInterceptor(sharedRateLimitService, 0);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(response, never()).sendError(anyInt(), anyString());
        verify(sharedRateLimitService, never()).tryAcquire(anyString(), anyInt());
    }
}
