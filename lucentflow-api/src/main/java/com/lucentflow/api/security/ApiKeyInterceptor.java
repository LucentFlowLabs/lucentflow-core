package com.lucentflow.api.security;

import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Lightweight project authentication using X-Project-Key.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-Project-Key";
    private final ProjectRepository projectRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Project-Key header");
            return false;
        }
        Optional<Project> projectOpt = projectRepository.findByApiKeyAndIsActiveTrue(apiKey.trim());
        if (projectOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid project key");
            return false;
        }
        ProjectContext.set(projectOpt.get());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ProjectContext.clear();
    }
}
