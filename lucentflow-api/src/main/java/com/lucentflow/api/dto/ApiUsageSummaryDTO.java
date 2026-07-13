package com.lucentflow.api.dto;

import java.util.List;

/**
 * Aggregated API usage summary for a project over a rolling window.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ApiUsageSummaryDTO(
        Long projectId,
        long totalRequests,
        long periodRequests,
        int days,
        List<ApiUsageDailyDTO> daily
) {
}
