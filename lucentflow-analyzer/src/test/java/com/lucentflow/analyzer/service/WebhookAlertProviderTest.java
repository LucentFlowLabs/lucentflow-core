package com.lucentflow.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for project-scoped webhook URL resolution (P0 gate fix).
 *
 * @author ArchLucent
 * @since 1.0
 */
class WebhookAlertProviderTest {

    @Test
    void resolveTargetWebhookUrl_prefersProjectUrlOverGlobal() {
        WebhookAlertProvider provider = newProvider("https://global.example/hook");
        AlertDispatchContext context = new AlertDispatchContext(
                true, "label", "cat", "0xabc", 7L, "https://project.example/hook");

        assertThat(provider.resolveTargetWebhookUrl(context)).isEqualTo("https://project.example/hook");
    }

    @Test
    void resolveTargetWebhookUrl_usesProjectUrlWhenGlobalBlank() {
        WebhookAlertProvider provider = newProvider("");
        AlertDispatchContext context = new AlertDispatchContext(
                false, null, null, null, 3L, " https://project-only.example/hook ");

        assertThat(provider.resolveTargetWebhookUrl(context)).isEqualTo("https://project-only.example/hook");
    }

    @Test
    void resolveTargetWebhookUrl_fallsBackToGlobalWhenProjectMissing() {
        WebhookAlertProvider provider = newProvider("https://global.example/hook");

        assertThat(provider.resolveTargetWebhookUrl(null)).isEqualTo("https://global.example/hook");
        assertThat(provider.resolveTargetWebhookUrl(
                new AlertDispatchContext(false, null, null, null, 1L, "  ")))
                .isEqualTo("https://global.example/hook");
    }

    @Test
    void resolveTargetWebhookUrl_returnsNullWhenNeitherConfigured() {
        WebhookAlertProvider provider = newProvider("");

        assertThat(provider.resolveTargetWebhookUrl(null)).isNull();
        assertThat(provider.resolveTargetWebhookUrl(
                new AlertDispatchContext(false, null, null, null, 1L, null)))
                .isNull();
    }

    private static WebhookAlertProvider newProvider(String globalUrl) {
        return new WebhookAlertProvider(
                new ObjectMapper(),
                new WebhookDeliveryStatusTracker(),
                globalUrl,
                "",
                1000L
        );
    }
}
