package com.lucentflow.api.service;

import com.lucentflow.common.ratelimit.SharedRateLimitService;
import com.lucentflow.common.repository.ProjectApiUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for project daily quota and shared per-minute rate limits.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class ProjectApiQuotaServiceTest {

    @Mock
    private ProjectApiUsageRepository projectApiUsageRepository;

    @Mock
    private SharedRateLimitService sharedRateLimitService;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void evaluate_rejectsWhenDailyQuotaExceeded() {
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectApiUsageRepository, sharedRateLimitService, 10, 0, clock);
        when(projectApiUsageRepository.sumRequestCountByProjectIdAndUsageDate(eq(1L), eq(LocalDate.of(2026, 7, 13))))
                .thenReturn(10L);

        assertThat(service.evaluate(1L)).contains("Daily request quota exceeded");
    }

    @Test
    void evaluate_rejectsWhenRateLimitExceeded() {
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectApiUsageRepository, sharedRateLimitService, 0, 2, clock);
        when(sharedRateLimitService.tryAcquire(eq("project:7"), eq(2)))
                .thenReturn(true, true, false);

        assertThat(service.evaluate(7L)).isEmpty();
        assertThat(service.evaluate(7L)).isEmpty();
        assertThat(service.evaluate(7L)).contains("Project rate limit exceeded");
    }

    @Test
    void evaluate_allowsWhenLimitsDisabled() {
        ProjectApiQuotaService service = ProjectApiQuotaService.forTests(
                projectApiUsageRepository, sharedRateLimitService, 0, 0, clock);
        assertThat(service.evaluate(3L)).isEmpty();
    }
}
