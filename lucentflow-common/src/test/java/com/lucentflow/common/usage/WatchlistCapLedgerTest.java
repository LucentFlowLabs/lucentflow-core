package com.lucentflow.common.usage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for atomic watchlist occupancy reservation.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WatchlistCapLedgerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void tryReserve_cappedRejectsWhenReturningNull() {
        WatchlistCapLedger ledger = new WatchlistCapLedger(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                eq(1L), eq(50)))
                .thenReturn(50L, null);

        assertThat(ledger.tryReserve(1L, 50)).isTrue();
        assertThat(ledger.tryReserve(1L, 50)).isFalse();
    }

    @Test
    void tryReserve_uncappedAlwaysIncrements() {
        WatchlistCapLedger ledger = new WatchlistCapLedger(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                eq(9L)))
                .thenReturn(1L);

        assertThat(ledger.tryReserve(9L, 0)).isTrue();
    }

    @Test
    void tryReserve_failClosedOnSqlError() {
        WatchlistCapLedger ledger = new WatchlistCapLedger(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(ledger.tryReserve(1L, 50)).isFalse();
    }

    @Test
    void tryReserve_nullProject_isRejectedWithoutJdbc() {
        WatchlistCapLedger ledger = new WatchlistCapLedger(jdbcTemplate);
        assertThat(ledger.tryReserve(null, 50)).isFalse();
    }

    @Test
    void release_decrementsOccupancy() {
        WatchlistCapLedger ledger = new WatchlistCapLedger(jdbcTemplate);

        ledger.release(42L);

        verify(jdbcTemplate).update(anyString(), eq(42L));
    }

    @Test
    void tryReserve_cappedSqlGuardsAddressCount() {
        WatchlistCapLedger ledger = new WatchlistCapLedger(jdbcTemplate);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                eq(1L), eq(50)))
                .thenReturn(1L);

        assertThat(ledger.tryReserve(1L, 50)).isTrue();
        assertThat(sql.getValue()).contains("WHERE project_watchlist_usage.address_count < ?");
    }
}
