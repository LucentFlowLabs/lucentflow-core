package com.lucentflow.api.security;

import com.lucentflow.api.service.ProjectApiQuotaService;
import com.lucentflow.api.service.ProjectApiUsageService;
import com.lucentflow.api.util.ProjectApiKeyGenerator;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Lightweight project authentication using X-Project-Key (hashed at rest).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-Project-Key";
    private final ProjectRepository projectRepository;
    private final ProjectApiUsageService projectApiUsageService;
    private final ProjectApiQuotaService projectApiQuotaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Project-Key header");
            return false;
        }
        String apiKeyHash = ProjectApiKeyGenerator.hash(apiKey.trim());
        Optional<Project> projectOpt = projectRepository.findByApiKeyHashAndIsActiveTrue(apiKeyHash);
        if (projectOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid project key");
            return false;
        }
        Project project = projectOpt.get();
        Optional<String> quotaRejection = projectApiQuotaService.evaluate(project.getId());
        if (quotaRejection.isPresent()) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), quotaRejection.get());
            return false;
        }
        ProjectContext.set(project);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            Long projectId = ProjectContext.getProjectId();
            if (projectId != null && isSuccessfulResponse(response)) {
                projectApiUsageService.recordRequestAsync(projectId);
            }
        } finally {
            ProjectContext.clear();
        }
    }

    private boolean isSuccessfulResponse(HttpServletResponse response) {
        int status = response.getStatus();
        return status >= 200 && status < 300;
    }
}
