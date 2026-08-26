package com.lucentflow.common.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Cluster-shared per-minute rate limiting via PostgreSQL UPSERT buckets.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Service
public class SharedRateLimitService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * Production constructor. {@code @Autowired} is required because a package-private
     * test constructor also exists; Spring otherwise tries a missing no-arg ctor.
     */
    @Autowired
    public SharedRateLimitService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    SharedRateLimitService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Atomically increment the minute bucket and allow when the new count is within the limit.
     * Rejects without incrementing past {@code limitPerMinute} ({@code WHERE request_count < limit}).
     *
     * @param bucketKey       e.g. {@code project:42} or {@code ip:1.2.3.4}
     * @param limitPerMinute  max permits in the current UTC minute; callers should skip when &lt;= 0
     * @return true if the request is within the limit
     */
    public boolean tryAcquire(String bucketKey, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        if (bucketKey == null || bucketKey.isBlank()) {
            return false;
        }
        long epochMinute = Instant.now(clock).getEpochSecond() / 60L;
        String sql = """
                INSERT INTO api_rate_limit_buckets (bucket_key, epoch_minute, request_count)
                VALUES (?, ?, 1)
                ON CONFLICT (bucket_key, epoch_minute)
                DO UPDATE SET request_count = api_rate_limit_buckets.request_count + 1
                WHERE api_rate_limit_buckets.request_count < ?
                RETURNING request_count
                """;
        try {
            Long count = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null,
                    bucketKey, epochMinute, limitPerMinute);
            return count != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Refund one minute-bucket permit (same UTC minute as {@link #tryAcquire}).
     * Used when a later admit (daily quota) rejects after this permit was taken.
     *
     * @param bucketKey e.g. {@code project:42}
     */
    public void release(String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return;
        }
        long epochMinute = Instant.now(clock).getEpochSecond() / 60L;
        try {
            jdbcTemplate.update("""
                    UPDATE api_rate_limit_buckets
                    SET request_count = GREATEST(request_count - 1, 0)
                    WHERE bucket_key = ? AND epoch_minute = ?
                    """, bucketKey, epochMinute);
        } catch (Exception ignored) {
            // callers must not fail the HTTP response on refund errors
        }
    }
}
