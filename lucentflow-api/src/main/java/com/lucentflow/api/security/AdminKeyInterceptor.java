package com.lucentflow.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin authentication using X-Admin-Key for project management APIs.
 *
 * @author ArchLucent
 * @since 1.0
 */
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
        if (providedKey == null || providedKey.isBlank() || !adminApiKey.equals(providedKey.trim())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin key");
            return false;
        }
        return true;
    }
}
