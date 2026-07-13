package com.lucentflow.common.ratelimit;

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

    public SharedRateLimitService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    SharedRateLimitService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Atomically increment the minute bucket and allow when {@code count <= limitPerMinute}.
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
                RETURNING request_count
                """;
        try {
            Long count = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null,
                    bucketKey, epochMinute);
            return count != null && count <= limitPerMinute;
        } catch (Exception ex) {
            return false;
        }
    }
}
