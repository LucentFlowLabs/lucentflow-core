package com.lucentflow.api.dto;

import java.time.LocalDate;

/**
 * Daily API usage bucket for a project.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ApiUsageDailyDTO(
        LocalDate date,
        long requestCount
) {
}
