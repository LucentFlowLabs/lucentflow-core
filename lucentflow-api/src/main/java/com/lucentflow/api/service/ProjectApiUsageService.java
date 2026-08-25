package com.lucentflow.api.service;

import com.lucentflow.api.dto.ApiUsageDailyDTO;
import com.lucentflow.api.dto.ApiUsageSummaryDTO;
import com.lucentflow.common.entity.ProjectApiUsage;
import com.lucentflow.common.repository.ProjectApiUsageRepository;
import com.lucentflow.common.usage.DailyApiUsageLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Queries per-project API usage counters and refunds non-2xx daily reservations.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class ProjectApiUsageService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_DAYS = 30;

    private final ProjectApiUsageRepository projectApiUsageRepository;
    private final DailyApiUsageLedger dailyApiUsageLedger;

    /**
     * Refund a reserved daily admit after a non-2xx response.
     *
     * @param projectId tenant id
     */
    public void releaseReservation(Long projectId) {
        dailyApiUsageLedger.release(projectId);
    }

    @Transactional(readOnly = true)
    public ApiUsageSummaryDTO getUsageSummary(Long projectId, Integer days) {
        int windowDays = normalizeDays(days);
        LocalDate fromDate = LocalDate.now().minusDays(windowDays - 1L);
        List<ProjectApiUsage> rows = projectApiUsageRepository
                .findByProjectIdAndUsageDateGreaterThanEqualOrderByUsageDateAsc(projectId, fromDate);

        long periodRequests = rows.stream()
                .mapToLong(row -> row.getRequestCount() == null ? 0L : row.getRequestCount())
                .sum();
        long totalRequests = projectApiUsageRepository.sumRequestCountByProjectId(projectId);

        List<ApiUsageDailyDTO> daily = rows.stream()
                .map(row -> new ApiUsageDailyDTO(
                        row.getUsageDate(),
                        row.getRequestCount() == null ? 0L : row.getRequestCount()))
                .toList();

        return new ApiUsageSummaryDTO(projectId, totalRequests, periodRequests, windowDays, daily);
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
    }
}
