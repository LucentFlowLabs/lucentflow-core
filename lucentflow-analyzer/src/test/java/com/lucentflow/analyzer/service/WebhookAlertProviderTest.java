package com.lucentflow.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.common.entity.WhaleTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for project-scoped webhook URL / HMAC secret resolution.
 *
 * @author ArchLucent
 * @since 1.0
 */
class WebhookAlertProviderTest {

    @Test
    void resolveTargetWebhookUrl_prefersProjectUrlOverGlobal() {
        WebhookAlertProvider provider = newProvider("https://global.example/hook", "global-secret");
        AlertDispatchContext context = new AlertDispatchContext(
                true, "label", "cat", "0xabc", 7L, "https://project.example/hook", "project-secret");

        assertThat(provider.resolveTargetWebhookUrl(context)).isEqualTo("https://project.example/hook");
    }

    @Test
    void resolveTargetWebhookUrl_usesProjectUrlWhenGlobalBlank() {
        WebhookAlertProvider provider = newProvider("", "");
        AlertDispatchContext context = new AlertDispatchContext(
                false, null, null, null, 3L, " https://project-only.example/hook ", null);

        assertThat(provider.resolveTargetWebhookUrl(context)).isEqualTo("https://project-only.example/hook");
    }

    @Test
    void resolveTargetWebhookUrl_fallsBackToGlobalWhenProjectMissing() {
        WebhookAlertProvider provider = newProvider("https://global.example/hook", "global-secret");

        assertThat(provider.resolveTargetWebhookUrl(null)).isEqualTo("https://global.example/hook");
        assertThat(provider.resolveTargetWebhookUrl(
                new AlertDispatchContext(false, null, null, null, 1L, "  ", null)))
                .isEqualTo("https://global.example/hook");
    }

    @Test
    void resolveTargetWebhookUrl_returnsNullWhenNeitherConfigured() {
        WebhookAlertProvider provider = newProvider("", "");

        assertThat(provider.resolveTargetWebhookUrl(null)).isNull();
        assertThat(provider.resolveTargetWebhookUrl(
                new AlertDispatchContext(false, null, null, null, 1L, null, null)))
                .isNull();
    }

    @Test
    void resolveSigningSecret_prefersProjectSecretOverGlobal() {
        WebhookAlertProvider provider = newProvider("https://global.example/hook", "global-secret");
        AlertDispatchContext context = new AlertDispatchContext(
                true, "l", "c", "0x1", 1L, "https://p.example/hook", " project-secret ");

        assertThat(provider.resolveSigningSecret(context)).isEqualTo("project-secret");
    }

    @Test
    void resolveSigningSecret_fallsBackToGlobal() {
        WebhookAlertProvider provider = newProvider("https://global.example/hook", "global-secret");

        assertThat(provider.resolveSigningSecret(
                new AlertDispatchContext(false, null, null, null, 1L, "https://p.example", null)))
                .isEqualTo("global-secret");
        assertThat(provider.resolveSigningSecret(null)).isEqualTo("global-secret");
    }

    @Test
    void doSendWithRetry_bulkheadTimeout_marksFailureWithoutSilentDrop() {
        WebhookDeliveryStatusTracker tracker = new WebhookDeliveryStatusTracker();
        WebhookAlertProvider provider = new WebhookAlertProvider(
                new ObjectMapper(),
                tracker,
                "https://global.example/hook",
                "global-secret",
                1000L,
                new Semaphore(0),
                0L);
        AlertDispatchContext context = new AlertDispatchContext(
                false, null, null, null, 9L, "https://project.example/hook", null);
        WhaleTransaction tx = WhaleTransaction.builder()
                .hash("0xdead")
                .fromAddress("0xfrom")
                .valueEth(BigDecimal.ONE)
                .blockNumber(1L)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .isContractCreation(false)
                .build();

        provider.doSendWithRetry(tx, context);

        assertThat(tracker.snapshot().failureCount()).isEqualTo(1);
        assertThat(tracker.snapshot().successCount()).isZero();
    }

    private static WebhookAlertProvider newProvider(String globalUrl, String globalSecret) {
        return new WebhookAlertProvider(
                new ObjectMapper(),
                new WebhookDeliveryStatusTracker(),
                globalUrl,
                globalSecret,
                1000L,
                new Semaphore(50),
                10_000L
        );
    }
}
