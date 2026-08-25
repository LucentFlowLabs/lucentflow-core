package com.lucentflow.api.service;

import com.lucentflow.common.entity.Project;
import com.lucentflow.common.ratelimit.SharedRateLimitService;
import com.lucentflow.common.repository.ProjectRepository;
import com.lucentflow.common.usage.DailyApiUsageLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for project daily quota reservation and shared per-minute rate limits.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class ProjectApiQuotaServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SharedRateLimitService sharedRateLimitService;

    @Mock
    private DailyApiUsageLedger dailyApiUsageLedger;

    @Test
    void evaluate_rejectsWhenDailyReserveFails() {
        Project project = Project.builder().id(1L).dailyRequestQuota(10).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectRepository, sharedRateLimitService, dailyApiUsageLedger, 2_000, 0);
        when(dailyApiUsageLedger.tryReserve(1L, 10)).thenReturn(false);

        assertThat(service.evaluate(1L)).contains("Daily request quota exceeded");
    }

    @Test
    void evaluate_reservesWhenUnderQuota() {
        Project project = Project.builder().id(1L).dailyRequestQuota(10).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectRepository, sharedRateLimitService, dailyApiUsageLedger, 2_000, 0);
        when(dailyApiUsageLedger.tryReserve(1L, 10)).thenReturn(true);

        assertThat(service.evaluate(1L)).isEmpty();
        verify(dailyApiUsageLedger).tryReserve(eq(1L), eq(10));
    }

    @Test
    void evaluate_rejectsWhenRateLimitExceeded() {
        Project project = Project.builder().id(7L).dailyRequestQuota(0).build();
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectRepository, sharedRateLimitService, dailyApiUsageLedger, 0, 2);
        when(sharedRateLimitService.tryAcquire(eq("project:7"), eq(2)))
                .thenReturn(true, true, false);
        when(dailyApiUsageLedger.tryReserve(7L, 0)).thenReturn(true);

        assertThat(service.evaluate(7L)).isEmpty();
        assertThat(service.evaluate(7L)).isEmpty();
        assertThat(service.evaluate(7L)).contains("Project rate limit exceeded");
    }

    @Test
    void evaluate_allowsWhenLimitsDisabled() {
        Project project = Project.builder().id(3L).dailyRequestQuota(0).build();
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));
        when(dailyApiUsageLedger.tryReserve(3L, 0)).thenReturn(true);
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectRepository, sharedRateLimitService, dailyApiUsageLedger, 0, 0);
        assertThat(service.evaluate(3L)).isEmpty();
        verify(dailyApiUsageLedger).tryReserve(eq(3L), eq(0));
    }
}
