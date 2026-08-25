package com.lucentflow.common.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Cluster-shared watchlist occupancy via PostgreSQL UPSERT.
 *
 * <p>Reserve is atomic ({@code WHERE address_count < limit} on conflict) so concurrent
 * creates cannot overshoot {@code projects.watchlist_limit}. Callers refund with
 * {@link #release(Long)} when the row insert fails or an address is deleted.</p>
 *
 * @author ArchLucent
 * @since 1.2
 */
@Service
public class WatchlistCapLedger {

    private final JdbcTemplate jdbcTemplate;

    public WatchlistCapLedger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically occupy one watchlist slot. When {@code watchlistLimit <= 0} there is no cap.
     *
     * @param projectId      tenant id
     * @param watchlistLimit max addresses; {@code 0} disables the cap
     * @return {@code true} if the slot is reserved
     */
    public boolean tryReserve(Long projectId, int watchlistLimit) {
        if (projectId == null) {
            return false;
        }
        String sql = watchlistLimit <= 0
                ? """
                INSERT INTO project_watchlist_usage (project_id, address_count, updated_at)
                VALUES (?, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (project_id)
                DO UPDATE SET
                    address_count = project_watchlist_usage.address_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING address_count
                """
                : """
                INSERT INTO project_watchlist_usage (project_id, address_count, updated_at)
                VALUES (?, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (project_id)
                DO UPDATE SET
                    address_count = project_watchlist_usage.address_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_watchlist_usage.address_count < ?
                RETURNING address_count
                """;
        try {
            Long count = watchlistLimit <= 0
                    ? jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null, projectId)
                    : jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null,
                            projectId, watchlistLimit);
            return count != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Refund one reserved slot (failed insert or delete). Missing rows are a no-op.
     *
     * @param projectId tenant id
     */
    public void release(Long projectId) {
        if (projectId == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE project_watchlist_usage
                    SET address_count = GREATEST(address_count - 1, 0),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE project_id = ?
                    """, projectId);
        } catch (Exception ignored) {
            // compensating decrement must not hide the original write error
        }
    }
}
