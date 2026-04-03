package com.lucentflow.indexer.config;

import com.lucentflow.indexer.control.AdaptiveBackpressureController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RpcConcurrencyGovernor#adjustForLag(long)}: catch-up boost, bounded drain,
 * and idempotency across repeated heartbeats.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RpcConcurrencyGovernor adjustForLag")
class RpcConcurrencyGovernorTest {

    @Mock
    private IndexerRpcProfile profile;

    private AdaptiveBackpressureController controller() {
        lenient().when(profile.effectivePollingIntervalMs()).thenReturn(2000L);
        lenient().when(profile.effectiveMaxBatchSizeCap()).thenReturn(200L);
        lenient().when(profile.effectiveCooldownWindowMs()).thenReturn(30_000L);
        lenient().when(profile.effectiveCooldownPollingIntervalMs()).thenReturn(10_000L);
        lenient().when(profile.effectiveCooldownMaxBatchSize()).thenReturn(20L);
        return new AdaptiveBackpressureController(profile);
    }

    private void stubBaseline(int baseline) {
        when(profile.effectiveMaxConcurrency()).thenReturn(baseline);
    }

    @Test
    @DisplayName("lag > 1000 adds extra permits up to cap; lag < 10 drains only that bonus")
    void boostThenDrain_restoresBaselinePool() {
        stubBaseline(10);
        when(profile.effectiveCatchupBoostEnabled()).thenReturn(true);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(controller(), profile);

        assertEquals(10, gov.availablePermits());

        gov.adjustForLag(2000L);
        assertEquals(10 * 2, gov.availablePermits());

        gov.adjustForLag(5L);
        assertEquals(10, gov.availablePermits());
    }

    @Test
    @DisplayName("repeated lag < 10 after full drain does not over-drain baseline")
    void repeatedLowLagAfterDrain_isIdempotent() {
        stubBaseline(10);
        when(profile.effectiveCatchupBoostEnabled()).thenReturn(true);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(controller(), profile);

        gov.adjustForLag(2000L);
        gov.adjustForLag(5L);
        assertEquals(10, gov.availablePermits());

        for (int i = 0; i < 20; i++) {
            gov.adjustForLag(5L);
        }
        assertEquals(10, gov.availablePermits());
    }

    @Test
    @DisplayName("lag > 1000 does not stack a second release while bonus is still outstanding")
    void highLagDoesNotDoubleBoostWhileBonusOutstanding() {
        stubBaseline(10);
        when(profile.effectiveCatchupBoostEnabled()).thenReturn(true);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(controller(), profile);

        gov.adjustForLag(2000L);
        assertEquals(10 * 2, gov.availablePermits());

        gov.adjustForLag(3000L);
        assertEquals(10 * 2, gov.availablePermits());
    }

    @Test
    @DisplayName("after full drain, a new lag spike can boost again")
    void afterDrain_secondHighLagBoostsAgain() {
        stubBaseline(10);
        when(profile.effectiveCatchupBoostEnabled()).thenReturn(true);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(controller(), profile);

        gov.adjustForLag(2000L);
        gov.adjustForLag(5L);
        assertEquals(10, gov.availablePermits());

        gov.adjustForLag(2000L);
        assertEquals(10 * 2, gov.availablePermits());
    }

    @Test
    @DisplayName("extra uses min(baseline, cap headroom), not always full baseline")
    void extraRespectsAlchemyCapWhenBaselineLarge() {
        stubBaseline(15);
        when(profile.effectiveCatchupBoostEnabled()).thenReturn(true);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(controller(), profile);

        int baseline = 15;
        int maxTotal = Math.min(20, baseline * 2);
        int extra = Math.min(baseline, maxTotal - baseline);

        assertEquals(baseline, gov.availablePermits());
        gov.adjustForLag(2000L);
        assertEquals(baseline + extra, gov.availablePermits());

        gov.adjustForLag(5L);
        assertEquals(baseline, gov.availablePermits());
    }
}
