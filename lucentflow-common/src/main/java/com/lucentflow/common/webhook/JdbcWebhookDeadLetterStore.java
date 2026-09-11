package com.lucentflow.common.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL-backed webhook dead-letter. Rows survive process restart; drain uses
 * {@code DELETE … RETURNING} with {@code FOR UPDATE SKIP LOCKED}.
 *
 * @author ArchLucent
 * @since 1.2
 */
@Service
@ConditionalOnProperty(name = "lucentflow.runtime.enable-analyzer", havingValue = "true", matchIfMissing = true)
public class JdbcWebhookDeadLetterStore implements WebhookDeadLetterStore {

    private static final RowMapper<WebhookDeadLetterRecord> ROW_MAPPER = JdbcWebhookDeadLetterStore::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final int capacity;

    public JdbcWebhookDeadLetterStore(
            JdbcTemplate jdbcTemplate,
            @Value("${lucentflow.webhook.dead-letter-capacity:500}") int capacity
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public boolean enqueue(WebhookDeadLetterRecord record) {
        if (record == null || record.txHash() == null || record.txHash().isBlank()) {
            return false;
        }
        String url = record.webhookUrl();
        if (url == null || url.isBlank()) {
            return false;
        }
        String payload = record.payloadJson() == null || record.payloadJson().isBlank()
                ? "{}"
                : record.payloadJson();
        Long id = jdbcTemplate.query("""
                INSERT INTO webhook_dead_letters (
                    tx_hash, project_id, webhook_url, watchlist_hit, watchlist_label,
                    watchlist_category, watchlist_address, payload_json, attempts
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?
                WHERE (SELECT COUNT(*) FROM webhook_dead_letters) < ?
                RETURNING id
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                record.txHash(),
                record.projectId(),
                url,
                record.watchlistHit(),
                record.watchlistLabel(),
                record.watchlistCategory(),
                record.watchlistAddress(),
                payload,
                record.attempts(),
                capacity);
        return id != null;
    }

    @Override
    public List<WebhookDeadLetterRecord> pollDue(int budget) {
        if (budget <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                DELETE FROM webhook_dead_letters
                WHERE id IN (
                    SELECT id FROM webhook_dead_letters
                    ORDER BY id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, tx_hash, project_id, webhook_url, watchlist_hit,
                          watchlist_label, watchlist_category, watchlist_address,
                          payload_json::text, attempts
                """,
                ROW_MAPPER,
                budget);
    }

    @Override
    public int size() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_dead_letters", Integer.class);
        return count == null ? 0 : count;
    }

    private static WebhookDeadLetterRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        long projectIdRaw = rs.getLong("project_id");
        Long projectId = rs.wasNull() ? null : projectIdRaw;
        return new WebhookDeadLetterRecord(
                rs.getLong("id"),
                rs.getString("tx_hash"),
                projectId,
                rs.getString("webhook_url"),
                rs.getBoolean("watchlist_hit"),
                rs.getString("watchlist_label"),
                rs.getString("watchlist_category"),
                rs.getString("watchlist_address"),
                rs.getString("payload_json"),
                rs.getInt("attempts")
        );
    }
}
