package com.lucentflow.indexer.config;

import com.lucentflow.sdk.config.RpcProviderConfig;
import com.lucentflow.sdk.config.RpcProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RpcConcurrencyGovernor#adjustForLag(long)}: catch-up boost, bounded drain,
 * and idempotency across repeated heartbeats.
 *
 * @author ArchLucent
 * @since 1.0
 */
@DisplayName("RpcConcurrencyGovernor adjustForLag")
class RpcConcurrencyGovernorTest {

    @Test
    @DisplayName("lag > 1000 adds extra permits up to cap; lag < 10 drains only that bonus")
    void boostThenDrain_restoresBaselinePool() {
        int baseline = 10;
        RpcProviderConfig cfg = new RpcProviderConfig(RpcProviderType.PROFESSIONAL, baseline, 50, 1500L);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(cfg);

        assertEquals(baseline, gov.availablePermits());

        gov.adjustForLag(2000L);
        assertEquals(baseline * 2, gov.availablePermits());

        gov.adjustForLag(5L);
        assertEquals(baseline, gov.availablePermits());
    }

    @Test
    @DisplayName("repeated lag < 10 after full drain does not over-drain baseline")
    void repeatedLowLagAfterDrain_isIdempotent() {
        int baseline = 10;
        RpcProviderConfig cfg = new RpcProviderConfig(RpcProviderType.PROFESSIONAL, baseline, 50, 1500L);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(cfg);

        gov.adjustForLag(2000L);
        gov.adjustForLag(5L);
        assertEquals(baseline, gov.availablePermits());

        for (int i = 0; i < 20; i++) {
            gov.adjustForLag(5L);
        }
        assertEquals(baseline, gov.availablePermits());
    }

    @Test
    @DisplayName("lag > 1000 does not stack a second release while bonus is still outstanding")
    void highLagDoesNotDoubleBoostWhileBonusOutstanding() {
        int baseline = 10;
        RpcProviderConfig cfg = new RpcProviderConfig(RpcProviderType.PROFESSIONAL, baseline, 50, 1500L);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(cfg);

        gov.adjustForLag(2000L);
        assertEquals(baseline * 2, gov.availablePermits());

        gov.adjustForLag(3000L);
        assertEquals(baseline * 2, gov.availablePermits());
    }

    @Test
    @DisplayName("after full drain, a new lag spike can boost again")
    void afterDrain_secondHighLagBoostsAgain() {
        int baseline = 10;
        RpcProviderConfig cfg = new RpcProviderConfig(RpcProviderType.PROFESSIONAL, baseline, 50, 1500L);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(cfg);

        gov.adjustForLag(2000L);
        gov.adjustForLag(5L);
        assertEquals(baseline, gov.availablePermits());

        gov.adjustForLag(2000L);
        assertEquals(baseline * 2, gov.availablePermits());
    }

    @Test
    @DisplayName("extra uses min(baseline, cap headroom), not always full baseline")
    void extraRespectsAlchemyCapWhenBaselineLarge() {
        int baseline = 15;
        RpcProviderConfig cfg = new RpcProviderConfig(RpcProviderType.PROFESSIONAL, baseline, 50, 1500L);
        RpcConcurrencyGovernor gov = new RpcConcurrencyGovernor(cfg);

        int maxTotal = Math.min(20, baseline * 2);
        int extra = Math.min(baseline, maxTotal - baseline);

        assertEquals(baseline, gov.availablePermits());
        gov.adjustForLag(2000L);
        assertEquals(baseline + extra, gov.availablePermits());

        gov.adjustForLag(5L);
        assertEquals(baseline, gov.availablePermits());
    }
}
