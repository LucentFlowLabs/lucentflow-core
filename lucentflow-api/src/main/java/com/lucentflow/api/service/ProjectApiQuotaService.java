package com.lucentflow.api.service;

import com.lucentflow.common.entity.Project;
import com.lucentflow.common.ratelimit.SharedRateLimitService;
import com.lucentflow.common.repository.ProjectRepository;
import com.lucentflow.common.usage.DailyApiUsageLedger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Enforces per-project daily quotas and cluster-shared per-minute rate limits.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
public class ProjectApiQuotaService {

    private final ProjectRepository projectRepository;
    private final SharedRateLimitService sharedRateLimitService;
    private final DailyApiUsageLedger dailyApiUsageLedger;

    private int dailyRequestQuotaFallback = 2_000;
    private int rateLimitPerMinute = 120;

    @org.springframework.beans.factory.annotation.Autowired
    public ProjectApiQuotaService(
            ProjectRepository projectRepository,
            SharedRateLimitService sharedRateLimitService,
            DailyApiUsageLedger dailyApiUsageLedger
    ) {
        this.projectRepository = projectRepository;
        this.sharedRateLimitService = sharedRateLimitService;
        this.dailyApiUsageLedger = dailyApiUsageLedger;
    }

    static ProjectApiQuotaService forTests(
            ProjectRepository projectRepository,
            SharedRateLimitService sharedRateLimitService,
            DailyApiUsageLedger dailyApiUsageLedger,
            int dailyRequestQuotaFallback,
            int rateLimitPerMinute
    ) {
        ProjectApiQuotaService service = new ProjectApiQuotaService(
                projectRepository, sharedRateLimitService, dailyApiUsageLedger);
        service.dailyRequestQuotaFallback = Math.max(0, dailyRequestQuotaFallback);
        service.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
        return service;
    }

    @Value("${lucentflow.api.daily-request-quota:2000}")
    void setDailyRequestQuotaFallback(int dailyRequestQuota) {
        this.dailyRequestQuotaFallback = Math.max(0, dailyRequestQuota);
    }

    @Value("${lucentflow.api.rate-limit-per-minute:120}")
    void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
    }

    /**
     * Atomically reserve a daily admit (and a per-minute permit). Empty means allowed.
     * A minute permit taken before a daily reject is refunded.
     *
     * @return rejection reason, or empty if the request is allowed
     */
    public Optional<String> evaluate(Long projectId) {
        if (projectId == null) {
            return Optional.of("Missing project context");
        }
        if (rateLimitPerMinute > 0 && !tryAcquireRatePermit(projectId)) {
            return Optional.of("Project rate limit exceeded");
        }
        int dailyQuota = resolveDailyQuota(projectId);
        if (!dailyApiUsageLedger.tryReserve(projectId, dailyQuota)) {
            if (rateLimitPerMinute > 0) {
                sharedRateLimitService.release("project:" + projectId);
            }
            return Optional.of("Daily request quota exceeded");
        }
        return Optional.empty();
    }

    int resolveDailyQuota(Long projectId) {
        Optional<Project> project = projectRepository.findById(projectId);
        if (project.isEmpty()) {
            return dailyRequestQuotaFallback;
        }
        Integer quota = project.get().getDailyRequestQuota();
        return quota == null ? dailyRequestQuotaFallback : Math.max(0, quota);
    }

    boolean tryAcquireRatePermit(Long projectId) {
        return sharedRateLimitService.tryAcquire("project:" + projectId, rateLimitPerMinute);
    }
}
