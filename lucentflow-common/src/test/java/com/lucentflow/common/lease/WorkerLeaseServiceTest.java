package com.lucentflow.common.lease;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for conditional UPSERT worker lease acquire/renew/release.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WorkerLeaseServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void tryAcquireOrRenew_returnsTrueWhenHolderMatches() {
        WorkerLeaseService service = new WorkerLeaseService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<String>>any(),
                eq("indexer-leader"), eq("pod-a"), eq(15L)))
                .thenReturn("pod-a");

        assertThat(service.tryAcquireOrRenew("indexer-leader", "pod-a", Duration.ofSeconds(15)))
                .isTrue();
    }

    @Test
    void tryAcquireOrRenew_returnsFalseWhenConflictOrEmpty() {
        WorkerLeaseService service = new WorkerLeaseService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<String>>any(),
                eq("indexer-leader"), eq("pod-b"), eq(15L)))
                .thenReturn(null);

        assertThat(service.tryAcquireOrRenew("indexer-leader", "pod-b", Duration.ofSeconds(15)))
                .isFalse();
    }

    @Test
    void release_deletesOnlyMatchingHolder() {
        WorkerLeaseService service = new WorkerLeaseService(jdbcTemplate);
        service.release("indexer-leader", "pod-a");
        verify(jdbcTemplate).update(
                eq("DELETE FROM worker_leases WHERE lease_name = ? AND holder_id = ?"),
                eq("indexer-leader"),
                eq("pod-a"));
    }
}
