package com.lucentflow.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for shared PostgreSQL minute-bucket rate limiting.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class SharedRateLimitServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void tryAcquire_allowsUpToLimitInclusive() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
        SharedRateLimitService service = new SharedRateLimitService(jdbcTemplate, clock);

        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Long>>any(),
                eq("project:1"), ArgumentMatchers.anyLong()))
                .thenReturn(1L, 2L, 3L);

        assertThat(service.tryAcquire("project:1", 2)).isTrue();
        assertThat(service.tryAcquire("project:1", 2)).isTrue();
        assertThat(service.tryAcquire("project:1", 2)).isFalse();
    }

    @Test
    void tryAcquire_disabledWhenLimitZero() {
        SharedRateLimitService service = new SharedRateLimitService(jdbcTemplate, Clock.systemUTC());
        assertThat(service.tryAcquire("project:9", 0)).isTrue();
    }
}
