package com.lucentflow.common.usage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for atomic daily usage reservation.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class DailyApiUsageLedgerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void tryReserve_cappedRejectsWhenReturningNull() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
        DailyApiUsageLedger ledger = new DailyApiUsageLedger(jdbcTemplate, clock);
        Date usageDate = Date.valueOf(LocalDate.of(2026, 7, 13));

        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                eq(1L), eq(usageDate), eq(10)))
                .thenReturn(10L, null);

        assertThat(ledger.tryReserve(1L, 10)).isTrue();
        assertThat(ledger.tryReserve(1L, 10)).isFalse();
    }

    @Test
    void tryReserve_uncappedAlwaysIncrements() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
        DailyApiUsageLedger ledger = new DailyApiUsageLedger(jdbcTemplate, clock);
        Date usageDate = Date.valueOf(LocalDate.of(2026, 7, 13));

        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                eq(9L), eq(usageDate)))
                .thenReturn(1L);

        assertThat(ledger.tryReserve(9L, 0)).isTrue();
    }

    @Test
    void tryReserve_failClosedOnSqlError() {
        DailyApiUsageLedger ledger = new DailyApiUsageLedger(jdbcTemplate, Clock.systemUTC());
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(ledger.tryReserve(1L, 10)).isFalse();
    }

    @Test
    void release_decrementsTodayUtc() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
        DailyApiUsageLedger ledger = new DailyApiUsageLedger(jdbcTemplate, clock);

        ledger.release(42L);

        verify(jdbcTemplate).update(anyString(), eq(42L), eq(Date.valueOf(LocalDate.of(2026, 7, 13))));
    }
}
