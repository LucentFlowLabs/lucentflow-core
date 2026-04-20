package com.lucentflow.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.common.entity.WhaleTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Generic outbound Webhook provider for high-risk alerts.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class WebhookAlertProvider implements AlertProvider {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = new long[]{1_000L, 2_000L, 4_000L};
    private static final Semaphore WEBHOOK_BULKHEAD = new Semaphore(50);
    private static final ExecutorService WEBHOOK_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String webhookUrl;
    private final String secretToken;

    public WebhookAlertProvider(
            ObjectMapper objectMapper,
            @Value("${lucentflow.webhook.url:}") String webhookUrl,
            @Value("${lucentflow.webhook.secret-token:}") String secretToken,
            @Value("${lucentflow.webhook.connect-timeout-ms:5000}") long connectTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.secretToken = secretToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000L, connectTimeoutMs)))
                .executor(WEBHOOK_EXECUTOR)
                .build();
    }

    @Override
    public void sendHighRiskAlertAsync(WhaleTransaction tx) {
        if (tx == null || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> doSendWithRetry(tx), WEBHOOK_EXECUTOR);
    }

    private void doSendWithRetry(WhaleTransaction tx) {
        boolean acquired = WEBHOOK_BULKHEAD.tryAcquire();
        if (!acquired) {
            log.warn("[WEBHOOK] Bulkhead saturated (50). Dropping tx {}", tx.getHash());
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(buildPayload(tx));
            URI targetUri = URI.create(webhookUrl.trim());

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(targetUri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                if (secretToken != null && !secretToken.isBlank()) {
                    requestBuilder.header("X-Webhook-Token", secretToken.trim());
                }
                HttpRequest request = requestBuilder.build();
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        return;
                    }
                    log.warn("[WEBHOOK] HTTP {} attempt {}/{} tx={}", statusCode, attempt, MAX_ATTEMPTS, tx.getHash());
                } catch (Exception e) {
                    log.warn("[WEBHOOK] send attempt {}/{} failed tx={} err={}", attempt, MAX_ATTEMPTS, tx.getHash(), e.getMessage());
                }

                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(BACKOFF_MS[attempt - 1]);
                }
            }
        } catch (Exception e) {
            log.warn("[WEBHOOK] payload/build failed tx={} err={}", tx.getHash(), e.getMessage());
        } finally {
            WEBHOOK_BULKHEAD.release();
        }
    }

    private Map<String, Object> buildPayload(WhaleTransaction tx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", "HIGH_RISK_WHALE_EVENT");
        payload.put("timestamp", Instant.now().toString());
        payload.put("transaction_hash", tx.getHash());
        payload.put("deep_link", tx.getHash() == null ? null : "https://basescan.org/tx/" + tx.getHash());

        Map<String, Object> riskAssessment = new LinkedHashMap<>();
        riskAssessment.put("risk_score", tx.getRiskScore());
        riskAssessment.put("risk_level", tx.getRugRiskLevel());
        riskAssessment.put("reasons", tx.getRiskReasons() == null ? Map.of() : tx.getRiskReasons());
        payload.put("risk_assessment", riskAssessment);
        return payload;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
