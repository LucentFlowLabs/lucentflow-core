package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Discord is operator-global and a no-op when the webhook URL is blank.
 *
 * @author ArchLucent
 * @since 1.2
 */
class DiscordAlertProviderTest {

    @Test
    void supportsProjectScopedDispatch_isFalse() {
        DiscordAlertProvider provider = new DiscordAlertProvider(new OkHttpClient(), "https://discord.example/hook");
        assertThat(provider.supportsProjectScopedDispatch()).isFalse();
    }

    @Test
    void sendHighRiskAlertAsync_blankUrl_doesNotCallHttp() {
        OkHttpClient http = mock(OkHttpClient.class);
        DiscordAlertProvider provider = new DiscordAlertProvider(http, "  ");
        provider.sendHighRiskAlertAsync(sampleTx(), null);
        verifyNoInteractions(http);
    }

    private static WhaleTransaction sampleTx() {
        return WhaleTransaction.builder()
                .hash("0xabc")
                .fromAddress("0xfrom")
                .valueEth(BigDecimal.ONE)
                .riskScore(80)
                .blockNumber(1L)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .isContractCreation(false)
                .build();
    }
}
