package com.lucentflow.api.security;

import com.lucentflow.api.config.ConditionalOnAdminSurfaceEnabled;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Admin authentication using X-Admin-Key for project management APIs.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnAdminSurfaceEnabled
@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final String adminApiKey;

    public AdminKeyInterceptor(@Value("${lucentflow.admin.api-key:}") String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (adminApiKey == null || adminApiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Admin API is not configured");
            return false;
        }
        String providedKey = request.getHeader(ADMIN_KEY_HEADER);
        if (providedKey == null || providedKey.isBlank() || !constantTimeEquals(adminApiKey, providedKey.trim())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin key");
            return false;
        }
        return true;
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
