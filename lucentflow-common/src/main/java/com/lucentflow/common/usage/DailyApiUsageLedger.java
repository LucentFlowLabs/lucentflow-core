package com.lucentflow.common.usage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Cluster-shared daily API usage counters via PostgreSQL UPSERT.
 *
 * <p>Reserve is atomic ({@code WHERE request_count < quota} on conflict) so concurrent
 * admits cannot overshoot. Callers refund with {@link #release(Long)} when the HTTP
 * response is not 2xx, preserving 2xx-only billing.</p>
 *
 * @author ArchLucent
 * @since 1.2
 */
@Service
public class DailyApiUsageLedger {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * Production constructor. {@code @Autowired} is required because a package-private
     * test constructor also exists; Spring otherwise tries a missing no-arg ctor.
     */
    @Autowired
    public DailyApiUsageLedger(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    DailyApiUsageLedger(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    LocalDate todayUtc() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    /**
     * Atomically count one admit. When {@code dailyQuota <= 0} there is no cap.
     *
     * @param projectId  tenant id
     * @param dailyQuota max 2xx-equivalent admits today; {@code 0} disables the cap
     * @return {@code true} if the request is admitted
     */
    public boolean tryReserve(Long projectId, int dailyQuota) {
        if (projectId == null) {
            return false;
        }
        Date usageDate = Date.valueOf(todayUtc());
        String sql = dailyQuota <= 0
                ? """
                INSERT INTO project_api_usage (project_id, usage_date, request_count, updated_at)
                VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (project_id, usage_date)
                DO UPDATE SET
                    request_count = project_api_usage.request_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING request_count
                """
                : """
                INSERT INTO project_api_usage (project_id, usage_date, request_count, updated_at)
                VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (project_id, usage_date)
                DO UPDATE SET
                    request_count = project_api_usage.request_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_api_usage.request_count < ?
                RETURNING request_count
                """;
        try {
            Long count = dailyQuota <= 0
                    ? jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null, projectId, usageDate)
                    : jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null,
                            projectId, usageDate, dailyQuota);
            return count != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Refund one reserved admit (non-2xx). Missing rows are a no-op.
     *
     * @param projectId tenant id
     */
    public void release(Long projectId) {
        if (projectId == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE project_api_usage
                    SET request_count = GREATEST(request_count - 1, 0),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE project_id = ? AND usage_date = ?
                    """, projectId, Date.valueOf(todayUtc()));
        } catch (Exception ignored) {
            // afterCompletion must not fail the response
        }
    }
}
