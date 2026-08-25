package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Watchlist hits bypass the score floor; other rule flags still apply.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleCacheServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private AlertRuleCacheService service;

    @BeforeEach
    void setUp() {
        service = new AlertRuleCacheService(alertRuleRepository);
        ReflectionTestUtils.setField(service, "globalRiskThreshold", 70);
    }

    @Test
    void shouldAlert_nullTx_isFalse() {
        assertThat(service.shouldAlert(rule(80, false, false, true), null, true)).isFalse();
    }

    @Test
    void shouldAlert_disabledRule_isFalseEvenOnWatchlistHit() {
        assertThat(service.shouldAlert(rule(10, false, false, false), whale(90, false), true)).isFalse();
    }

    @Test
    void shouldAlert_watchlistHit_bypassesMinScore() {
        assertThat(service.shouldAlert(rule(90, false, false, true), whale(10, false), true)).isTrue();
    }

    @Test
    void shouldAlert_watchlistOnly_withoutHit_isFalse() {
        assertThat(service.shouldAlert(rule(10, true, false, true), whale(100, false), false)).isFalse();
    }

    @Test
    void shouldAlert_contractCreationOnly_rejectsNonCreationEvenOnWatchlist() {
        assertThat(service.shouldAlert(rule(10, false, true, true), whale(100, false), true)).isFalse();
        assertThat(service.shouldAlert(rule(10, false, true, true), whale(100, true), true)).isTrue();
    }

    @Test
    void shouldAlert_scoreAtThreshold_alertsWithoutWatchlist() {
        assertThat(service.shouldAlert(rule(80, false, false, true), whale(80, false), false)).isTrue();
        assertThat(service.shouldAlert(rule(80, false, false, true), whale(79, false), false)).isFalse();
    }

    @Test
    void shouldAlert_nullRule_usesGlobalThreshold() {
        assertThat(service.shouldAlert(null, whale(70, false), false)).isTrue();
        assertThat(service.shouldAlert(null, whale(69, false), false)).isFalse();
    }

    private static AlertRuleCacheService.ProjectAlertRule rule(
            int minScore, boolean watchlistOnly, boolean contractCreationOnly, boolean enabled) {
        return new AlertRuleCacheService.ProjectAlertRule(
                1L, null, null, minScore, watchlistOnly, contractCreationOnly, enabled);
    }

    private static WhaleTransaction whale(int score, boolean contractCreation) {
        return WhaleTransaction.builder()
                .hash("0xabc")
                .fromAddress("0xfrom")
                .valueEth(BigDecimal.ONE)
                .riskScore(score)
                .blockNumber(1L)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .isContractCreation(contractCreation)
                .build();
    }
}
