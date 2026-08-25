package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Global channels send once with a merged context; webhooks still fan out per project.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private WatchlistCacheService watchlistCacheService;
    @Mock
    private AlertRuleCacheService alertRuleCacheService;

    @Test
    void mergeForGlobalChannel_prefersLaterWatchlistHitOverFirstScoreOnlyContext() {
        AlertDispatchContext scoreOnly = new AlertDispatchContext(
                false, null, null, null, 1L, "https://a.example", null);
        AlertDispatchContext watchlist = new AlertDispatchContext(
                true, "Insider", "SUSPECT", "0xabc", 2L, "https://b.example", null);

        AlertDispatchContext merged = AlertDispatchContext.mergeForGlobalChannel(List.of(scoreOnly, watchlist));

        assertThat(merged.watchlistHit()).isTrue();
        assertThat(merged.watchlistLabel()).isEqualTo("Insider");
        assertThat(merged.projectId()).isEqualTo(2L);
    }

    @Test
    void mergeForGlobalChannel_joinsDistinctWatchlistLabels() {
        AlertDispatchContext a = new AlertDispatchContext(
                true, "Alpha", "cat", "0x1", 1L, null, null);
        AlertDispatchContext b = new AlertDispatchContext(
                true, "Beta", "cat", "0x2", 2L, null, null);

        AlertDispatchContext merged = AlertDispatchContext.mergeForGlobalChannel(List.of(a, b));

        assertThat(merged.watchlistHit()).isTrue();
        assertThat(merged.watchlistLabel()).isEqualTo("Alpha, Beta");
    }

    @Test
    void sendAlertIfNeeded_globalProviderOnce_projectProviderPerContext() {
        RecordingProvider global = new RecordingProvider(false);
        RecordingProvider webhook = new RecordingProvider(true);
        AlertService service = new AlertService(
                List.of(global, webhook), watchlistCacheService, alertRuleCacheService);

        WatchlistCacheService.WatchlistHit hit1 = new WatchlistCacheService.WatchlistHit(
                "0xfrom", "Alpha", "SUSPECT", 1L, "https://p1.example", "s1");
        WatchlistCacheService.WatchlistHit hit2 = new WatchlistCacheService.WatchlistHit(
                "0xfrom", "Beta", "SUSPECT", 2L, "https://p2.example", "s2");
        when(watchlistCacheService.findHits("0xfrom", "0xto")).thenReturn(List.of(hit1, hit2));
        when(alertRuleCacheService.ruleForProject(any())).thenReturn(null);
        when(alertRuleCacheService.shouldAlert(any(), any(), eq(true))).thenReturn(true);
        when(alertRuleCacheService.allRules()).thenReturn(List.of());

        service.sendAlertIfNeeded(sampleTx());

        assertThat(global.received).hasSize(1);
        assertThat(global.received.getFirst().watchlistHit()).isTrue();
        assertThat(global.received.getFirst().watchlistLabel()).isEqualTo("Alpha, Beta");
        assertThat(webhook.received).hasSize(2);
        assertThat(webhook.received)
                .extracting(AlertDispatchContext::projectId)
                .containsExactly(1L, 2L);
    }

    private static WhaleTransaction sampleTx() {
        return WhaleTransaction.builder()
                .hash("0xabc")
                .fromAddress("0xfrom")
                .toAddress("0xto")
                .valueEth(BigDecimal.ONE)
                .riskScore(80)
                .blockNumber(1L)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .isContractCreation(false)
                .build();
    }

    private static final class RecordingProvider implements AlertProvider {
        private final boolean projectScoped;
        private final List<AlertDispatchContext> received = new ArrayList<>();

        private RecordingProvider(boolean projectScoped) {
            this.projectScoped = projectScoped;
        }

        @Override
        public void sendHighRiskAlertAsync(WhaleTransaction tx, AlertDispatchContext context) {
            received.add(context);
        }

        @Override
        public boolean supportsProjectScopedDispatch() {
            return projectScoped;
        }
    }
}
