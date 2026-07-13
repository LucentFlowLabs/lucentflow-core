package com.lucentflow.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

    private static WebhookAlertProvider newProvider(String globalUrl, String globalSecret) {
        return new WebhookAlertProvider(
                new ObjectMapper(),
                new WebhookDeliveryStatusTracker(),
                globalUrl,
                globalSecret,
                1000L
        );
    }
}
