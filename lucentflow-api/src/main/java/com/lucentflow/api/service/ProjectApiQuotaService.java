package com.lucentflow.api.service;

import com.lucentflow.common.ratelimit.SharedRateLimitService;
import com.lucentflow.common.repository.ProjectApiUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Enforces per-project daily quotas and cluster-shared per-minute rate limits.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class ProjectApiQuotaService {

    private final ProjectApiUsageRepository projectApiUsageRepository;
    private final SharedRateLimitService sharedRateLimitService;
    private final Clock clock;

    private int dailyRequestQuota = 100_000;
    private int rateLimitPerMinute = 120;

    @org.springframework.beans.factory.annotation.Autowired
    public ProjectApiQuotaService(
            ProjectApiUsageRepository projectApiUsageRepository,
            SharedRateLimitService sharedRateLimitService
    ) {
        this.projectApiUsageRepository = projectApiUsageRepository;
        this.sharedRateLimitService = sharedRateLimitService;
        this.clock = Clock.systemUTC();
    }

    static ProjectApiQuotaService forTests(
            ProjectApiUsageRepository projectApiUsageRepository,
            SharedRateLimitService sharedRateLimitService,
            int dailyRequestQuota,
            int rateLimitPerMinute,
            Clock clock
    ) {
        ProjectApiQuotaService service = new ProjectApiQuotaService(
                projectApiUsageRepository, sharedRateLimitService, clock);
        service.dailyRequestQuota = Math.max(0, dailyRequestQuota);
        service.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
        return service;
    }

    private ProjectApiQuotaService(
            ProjectApiUsageRepository projectApiUsageRepository,
            SharedRateLimitService sharedRateLimitService,
            Clock clock
    ) {
        this.projectApiUsageRepository = projectApiUsageRepository;
        this.sharedRateLimitService = sharedRateLimitService;
        this.clock = clock;
    }

    @Value("${lucentflow.api.daily-request-quota:100000}")
    void setDailyRequestQuota(int dailyRequestQuota) {
        this.dailyRequestQuota = Math.max(0, dailyRequestQuota);
    }

    @Value("${lucentflow.api.rate-limit-per-minute:120}")
    void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
    }

    /**
     * @return rejection reason, or empty if the request is allowed
     */
    public Optional<String> evaluate(Long projectId) {
        if (projectId == null) {
            return Optional.of("Missing project context");
        }
        if (rateLimitPerMinute > 0 && !tryAcquireRatePermit(projectId)) {
            return Optional.of("Project rate limit exceeded");
        }
        if (dailyRequestQuota > 0) {
            long usedToday = projectApiUsageRepository.sumRequestCountByProjectIdAndUsageDate(
                    projectId, LocalDate.now(clock));
            if (usedToday >= dailyRequestQuota) {
                return Optional.of("Daily request quota exceeded");
            }
        }
        return Optional.empty();
    }

    boolean tryAcquireRatePermit(Long projectId) {
        return sharedRateLimitService.tryAcquire("project:" + projectId, rateLimitPerMinute);
    }
}
