package com.lucentflow.common.webhook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Native SQL contract for the webhook dead-letter table (capacity + SKIP LOCKED poll).
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class JdbcWebhookDeadLetterStoreTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void enqueue_insertsWhenUnderCapacity() {
        JdbcWebhookDeadLetterStore store = new JdbcWebhookDeadLetterStore(jdbcTemplate, 500);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(500)))
                .thenReturn(1L);

        boolean accepted = store.enqueue(sampleRecord());

        assertThat(accepted).isTrue();
        assertThat(sql.getValue()).contains("WHERE (SELECT COUNT(*) FROM webhook_dead_letters) < ?");
        assertThat(sql.getValue()).contains("CAST(? AS JSONB)");
    }

    @Test
    void enqueue_rejectsWhenInsertReturnsNoRow() {
        JdbcWebhookDeadLetterStore store = new JdbcWebhookDeadLetterStore(jdbcTemplate, 2);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(2)))
                .thenReturn(null);

        assertThat(store.enqueue(sampleRecord())).isFalse();
    }

    @Test
    void pollDue_usesSkipLockedDeleteReturning() {
        JdbcWebhookDeadLetterStore store = new JdbcWebhookDeadLetterStore(jdbcTemplate, 500);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), ArgumentMatchers.<RowMapper<WebhookDeadLetterRecord>>any(), eq(16)))
                .thenReturn(List.of());

        assertThat(store.pollDue(16)).isEmpty();
        assertThat(sql.getValue()).contains("FOR UPDATE SKIP LOCKED");
        assertThat(sql.getValue()).contains("DELETE FROM webhook_dead_letters");
    }

    @Test
    void enqueue_blankHash_isRejectedWithoutJdbc() {
        JdbcWebhookDeadLetterStore store = new JdbcWebhookDeadLetterStore(jdbcTemplate, 500);
        WebhookDeadLetterRecord blank = new WebhookDeadLetterRecord(
                null, "  ", null, "https://hook.example", false, null, null, null, "{}", 1);
        assertThat(store.enqueue(blank)).isFalse();
    }

    private static WebhookDeadLetterRecord sampleRecord() {
        return new WebhookDeadLetterRecord(
                null,
                "0xabc",
                7L,
                "https://project.example/hook",
                true,
                "label",
                "cat",
                "0xabc",
                "{\"hash\":\"0xabc\"}",
                1
        );
    }
}
