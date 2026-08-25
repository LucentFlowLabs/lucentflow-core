package com.lucentflow.api.security;

import com.lucentflow.api.service.ProjectApiQuotaService;
import com.lucentflow.api.service.ProjectApiUsageService;
import com.lucentflow.api.util.ProjectApiKeyGenerator;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for hashed project-key authentication and quota enforcement.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyInterceptorTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectApiUsageService projectApiUsageService;
    @Mock
    private ProjectApiQuotaService projectApiQuotaService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private ApiKeyInterceptor interceptor;

    @AfterEach
    void clearContext() {
        ProjectContext.clear();
    }

    @Test
    void preHandle_rejectsMissingKey() throws Exception {
        when(request.getHeader("X-Project-Key")).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(eq(401), anyString());
    }

    @Test
    void preHandle_authenticatesHashedKeyAndSetsContext() throws Exception {
        String plaintext = "demo-project-key-2026";
        Project project = Project.builder()
                .id(42L)
                .name("Demo")
                .apiKeyHash(ProjectApiKeyGenerator.hash(plaintext))
                .apiKeyPrefix("demo-pro")
                .isActive(true)
                .build();
        when(request.getHeader("X-Project-Key")).thenReturn(plaintext);
        when(projectRepository.findByApiKeyHashAndIsActiveTrue(ProjectApiKeyGenerator.hash(plaintext)))
                .thenReturn(Optional.of(project));
        when(projectApiQuotaService.evaluate(42L)).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(ProjectContext.getProjectId()).isEqualTo(42L);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void preHandle_rejectsWhenQuotaExceeded() throws Exception {
        String plaintext = "lfproj_testkey";
        Project project = Project.builder()
                .id(9L)
                .name("P")
                .apiKeyHash(ProjectApiKeyGenerator.hash(plaintext))
                .apiKeyPrefix("lfproj_t")
                .isActive(true)
                .build();
        when(request.getHeader("X-Project-Key")).thenReturn(plaintext);
        when(projectRepository.findByApiKeyHashAndIsActiveTrue(ProjectApiKeyGenerator.hash(plaintext)))
                .thenReturn(Optional.of(project));
        when(projectApiQuotaService.evaluate(9L)).thenReturn(Optional.of("Daily request quota exceeded"));

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(eq(429), eq("Daily request quota exceeded"));
    }

    @Test
    void afterCompletion_releasesReservationOnNon2xx() {
        Project project = Project.builder().id(42L).name("Demo").isActive(true).build();
        ProjectContext.set(project);
        when(response.getStatus()).thenReturn(504);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(projectApiUsageService).releaseReservation(42L);
        assertThat(ProjectContext.get()).isNull();
    }

    @Test
    void afterCompletion_keepsReservationOn2xx() {
        Project project = Project.builder().id(42L).name("Demo").isActive(true).build();
        ProjectContext.set(project);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(projectApiUsageService, never()).releaseReservation(any());
    }
}
