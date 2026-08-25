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
 * Telegram is operator-global and a no-op when bot token or chat id is blank.
 *
 * @author ArchLucent
 * @since 1.2
 */
class TelegramAlertProviderTest {

    @Test
    void supportsProjectScopedDispatch_isFalse() {
        TelegramAlertProvider provider = new TelegramAlertProvider(new OkHttpClient(), "token", "chat");
        assertThat(provider.supportsProjectScopedDispatch()).isFalse();
    }

    @Test
    void sendHighRiskAlertAsync_blankCredentials_doesNotCallHttp() {
        OkHttpClient http = mock(OkHttpClient.class);
        TelegramAlertProvider missingToken = new TelegramAlertProvider(http, "", "chat");
        TelegramAlertProvider missingChat = new TelegramAlertProvider(http, "token", " ");
        missingToken.sendHighRiskAlertAsync(sampleTx(), null);
        missingChat.sendHighRiskAlertAsync(sampleTx(), null);
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
