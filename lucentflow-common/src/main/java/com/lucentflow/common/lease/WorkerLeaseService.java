package com.lucentflow.common.lease;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * PostgreSQL TTL lease acquire / renew / release for worker leader election.
 * Uses conditional UPSERT so Hikari pooling stays compatible (no session advisory locks).
 *
 * @author ArchLucent
 * @since 1.2
 */
@Service
public class WorkerLeaseService {

    private final JdbcTemplate jdbcTemplate;

    public WorkerLeaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Attempt to acquire or renew {@code leaseName} for {@code holderId}.
     *
     * @return true if this holder owns the lease after the call
     */
    public boolean tryAcquireOrRenew(String leaseName, String holderId, Duration ttl) {
        if (leaseName == null || leaseName.isBlank() || holderId == null || holderId.isBlank()) {
            return false;
        }
        long ttlSeconds = Math.max(1L, ttl.toSeconds());
        String sql = """
                INSERT INTO worker_leases (lease_name, holder_id, lease_until, updated_at)
                VALUES (?, ?, NOW() + (? * INTERVAL '1 second'), NOW())
                ON CONFLICT (lease_name) DO UPDATE SET
                    holder_id = EXCLUDED.holder_id,
                    lease_until = EXCLUDED.lease_until,
                    updated_at = EXCLUDED.updated_at
                WHERE worker_leases.holder_id = EXCLUDED.holder_id
                   OR worker_leases.lease_until < NOW()
                RETURNING holder_id
                """;
        try {
            String holder = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null,
                    leaseName, holderId, ttlSeconds);
            return holderId.equals(holder);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Release the lease only if still held by {@code holderId}.
     */
    public void release(String leaseName, String holderId) {
        if (leaseName == null || leaseName.isBlank() || holderId == null || holderId.isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "DELETE FROM worker_leases WHERE lease_name = ? AND holder_id = ?",
                leaseName, holderId);
    }
}
