package com.lucentflow.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.common.entity.WhaleTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Generic outbound Webhook provider for high-risk alerts.
 * Bulkhead saturation enqueues a bounded in-memory dead-letter for retry.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class WebhookAlertProvider implements AlertProvider {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = new long[]{1_000L, 2_000L, 4_000L};
    private static final ExecutorService WEBHOOK_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final String SIGNATURE_HEADER = "X-LucentFlow-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String webhookUrl;
    private final String secretToken;
    private final WebhookDeliveryStatusTracker deliveryStatusTracker;
    private final Semaphore webhookBulkhead;
    private final long bulkheadAcquireTimeoutMs;
    private final BlockingQueue<DeadLetterItem> deadLetter;
    private final int deadLetterMaxAttempts;

    /**
     * In-memory retry after bulkhead saturation. Bounded so a down webhook cannot OOM the worker.
     *
     * @param tx       whale that still needs delivery
     * @param context  dispatch metadata (project URL / HMAC)
     * @param attempts DLQ cycles already used (1 after the first bulkhead miss)
     */
    record DeadLetterItem(WhaleTransaction tx, AlertDispatchContext context, int attempts) {
    }

    @Autowired
    public WebhookAlertProvider(
            ObjectMapper objectMapper,
            WebhookDeliveryStatusTracker deliveryStatusTracker,
            @Value("${lucentflow.webhook.url:}") String webhookUrl,
            @Value("${lucentflow.webhook.secret-token:}") String secretToken,
            @Value("${lucentflow.webhook.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${lucentflow.webhook.bulkhead-permits:50}") int bulkheadPermits,
            @Value("${lucentflow.webhook.bulkhead-acquire-timeout-ms:10000}") long bulkheadAcquireTimeoutMs,
            @Value("${lucentflow.webhook.dead-letter-capacity:500}") int deadLetterCapacity,
            @Value("${lucentflow.webhook.dead-letter-max-attempts:8}") int deadLetterMaxAttempts
    ) {
        this(
                objectMapper,
                deliveryStatusTracker,
                webhookUrl,
                secretToken,
                connectTimeoutMs,
                new Semaphore(Math.max(1, bulkheadPermits)),
                Math.max(0L, bulkheadAcquireTimeoutMs),
                Math.max(1, deadLetterCapacity),
                Math.max(1, deadLetterMaxAttempts)
        );
    }

    WebhookAlertProvider(
            ObjectMapper objectMapper,
            WebhookDeliveryStatusTracker deliveryStatusTracker,
            String webhookUrl,
            String secretToken,
            long connectTimeoutMs,
            Semaphore webhookBulkhead,
            long bulkheadAcquireTimeoutMs
    ) {
        this(
                objectMapper,
                deliveryStatusTracker,
                webhookUrl,
                secretToken,
                connectTimeoutMs,
                webhookBulkhead,
                bulkheadAcquireTimeoutMs,
                500,
                8
        );
    }

    WebhookAlertProvider(
            ObjectMapper objectMapper,
            WebhookDeliveryStatusTracker deliveryStatusTracker,
            String webhookUrl,
            String secretToken,
            long connectTimeoutMs,
            Semaphore webhookBulkhead,
            long bulkheadAcquireTimeoutMs,
            int deadLetterCapacity,
            int deadLetterMaxAttempts
    ) {
        this.objectMapper = objectMapper;
        this.deliveryStatusTracker = deliveryStatusTracker;
        this.webhookUrl = webhookUrl;
        this.secretToken = secretToken;
        this.webhookBulkhead = webhookBulkhead;
        this.bulkheadAcquireTimeoutMs = bulkheadAcquireTimeoutMs;
        this.deadLetter = new ArrayBlockingQueue<>(Math.max(1, deadLetterCapacity));
        this.deadLetterMaxAttempts = Math.max(1, deadLetterMaxAttempts);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000L, connectTimeoutMs)))
                .executor(WEBHOOK_EXECUTOR)
                .build();
    }

    @PostConstruct
    void logSignatureVerificationGuide() {
        if (secretToken == null || secretToken.isBlank()) {
            return;
        }
        log.info("[WEBHOOK] Signature enabled. Header={} format=t=<timestamp>,v1=<hmac_sha256_hex>", SIGNATURE_HEADER);
        log.info("[WEBHOOK] Node.js verify sample: const payload=`${{timestamp}}.${{rawBody}}`; const sig=crypto.createHmac('sha256', secret).update(payload).digest('hex');");
        log.info("[WEBHOOK] Java verify sample: Mac mac=Mac.getInstance(\"HmacSHA256\"); mac.init(new SecretKeySpec(secret.getBytes(UTF_8),\"HmacSHA256\"));");
    }

    @Override
    public void sendHighRiskAlertAsync(WhaleTransaction tx, AlertDispatchContext context) {
        if (tx == null) {
            return;
        }
        // Project webhook OR global fallback — do not require lucentflow.webhook.url when
        // AlertDispatchContext already carries a project-scoped URL.
        if (resolveTargetWebhookUrl(context) == null) {
            return;
        }
        CompletableFuture.runAsync(() -> doSendWithRetry(tx, context), WEBHOOK_EXECUTOR);
    }

    @Override
    public boolean supportsProjectScopedDispatch() {
        return true;
    }

    void doSendWithRetry(WhaleTransaction tx, AlertDispatchContext context) {
        doSendWithRetry(tx, context, 0);
    }

    void doSendWithRetry(WhaleTransaction tx, AlertDispatchContext context, int deadLetterAttempts) {
        boolean acquired;
        try {
            acquired = webhookBulkhead.tryAcquire(bulkheadAcquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[WEBHOOK] Interrupted while waiting for bulkhead tx={}; enqueueing dead-letter", tx.getHash());
            enqueueDeadLetter(tx, context, deadLetterAttempts + 1);
            return;
        }
        if (!acquired) {
            log.warn("[WEBHOOK] Bulkhead saturated after {} ms. Enqueueing dead-letter tx={} attempt={}",
                    bulkheadAcquireTimeoutMs, tx.getHash(), deadLetterAttempts + 1);
            enqueueDeadLetter(tx, context, deadLetterAttempts + 1);
            return;
        }
        try {
            String targetUrl = resolveTargetWebhookUrl(context);
            if (targetUrl == null || targetUrl.isBlank()) {
                return;
            }
            String payload = objectMapper.writeValueAsString(buildPayload(tx, context));
            URI targetUri = URI.create(targetUrl);

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(targetUri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                String signingSecret = resolveSigningSecret(context);
                if (signingSecret != null) {
                    requestBuilder.header(SIGNATURE_HEADER, buildSignatureHeader(timestamp, payload, signingSecret));
                }
                HttpRequest request = requestBuilder.build();
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        deliveryStatusTracker.markSuccess(context == null ? null : context.projectId());
                        return;
                    }
                    deliveryStatusTracker.markFailure(context == null ? null : context.projectId());
                    log.warn("[WEBHOOK] HTTP {} attempt {}/{} tx={}", statusCode, attempt, MAX_ATTEMPTS, tx.getHash());
                } catch (Exception e) {
                    deliveryStatusTracker.markFailure(context == null ? null : context.projectId());
                    log.warn("[WEBHOOK] send attempt {}/{} failed tx={} err={}", attempt, MAX_ATTEMPTS, tx.getHash(), e.getMessage());
                }

                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(BACKOFF_MS[attempt - 1]);
                }
            }
        } catch (Exception e) {
            deliveryStatusTracker.markFailure(context == null ? null : context.projectId());
            log.warn("[WEBHOOK] payload/build failed tx={} err={}", tx.getHash(), e.getMessage());
        } finally {
            webhookBulkhead.release();
        }
    }

    /**
     * Retry a bounded batch of bulkhead-deferred deliveries. Package-visible for tests.
     */
    @Scheduled(fixedDelayString = "${lucentflow.webhook.dead-letter-drain-ms:500}")
    void drainDeadLetter() {
        int budget = 16;
        while (budget-- > 0) {
            DeadLetterItem item = deadLetter.poll();
            if (item == null) {
                return;
            }
            doSendWithRetry(item.tx(), item.context(), item.attempts());
        }
    }

    int deadLetterSize() {
        return deadLetter.size();
    }

    private void enqueueDeadLetter(WhaleTransaction tx, AlertDispatchContext context, int nextAttempts) {
        if (nextAttempts > deadLetterMaxAttempts) {
            log.error("[WEBHOOK] Dead-letter exhausted after {} attempts tx={}", deadLetterMaxAttempts, tx.getHash());
            deliveryStatusTracker.markFailure(context == null ? null : context.projectId());
            return;
        }
        if (!deadLetter.offer(new DeadLetterItem(tx, context, nextAttempts))) {
            log.error("[WEBHOOK] Dead-letter full; dropping tx={}", tx.getHash());
            deliveryStatusTracker.markFailure(context == null ? null : context.projectId());
        }
    }

    @PreDestroy
    void logPendingDeadLetters() {
        int remaining = deadLetter.size();
        if (remaining > 0) {
            log.error("[WEBHOOK] Destroying with {} undelivered dead-letter webhook(s)", remaining);
        }
    }

    private Map<String, Object> buildPayload(WhaleTransaction tx, AlertDispatchContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", "HIGH_RISK_WHALE_EVENT");
        payload.put("timestamp", Instant.now().toString());
        payload.put("transaction_hash", tx.getHash());
        payload.put("deep_link", tx.getHash() == null ? null : "https://basescan.org/tx/" + tx.getHash());
        payload.put("is_watchlist_hit", context != null && context.watchlistHit());
        payload.put("watchlist_label", context == null ? null : context.watchlistLabel());
        payload.put("watchlist_category", context == null ? null : context.watchlistCategory());
        payload.put("watchlist_address", context == null ? null : context.watchlistAddress());
        payload.put("project_id", context == null ? null : context.projectId());

        Map<String, Object> riskAssessment = new LinkedHashMap<>();
        riskAssessment.put("risk_score", tx.getRiskScore());
        riskAssessment.put("risk_level", tx.getRugRiskLevel());
        riskAssessment.put("reasons", tx.getRiskReasons() == null ? Map.of() : tx.getRiskReasons());
        payload.put("risk_assessment", riskAssessment);
        return payload;
    }

    /**
     * Prefer project webhook URL; fall back to global {@code lucentflow.webhook.url}.
     * Returns {@code null} when neither is configured.
     */
    String resolveTargetWebhookUrl(AlertDispatchContext context) {
        if (context != null && context.projectWebhookUrl() != null && !context.projectWebhookUrl().isBlank()) {
            return context.projectWebhookUrl().trim();
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return null;
        }
        return webhookUrl.trim();
    }

    /**
     * Prefer project webhook secret; fall back to global {@code lucentflow.webhook.secret-token}.
     */
    String resolveSigningSecret(AlertDispatchContext context) {
        if (context != null && context.projectWebhookSecret() != null && !context.projectWebhookSecret().isBlank()) {
            return context.projectWebhookSecret().trim();
        }
        if (secretToken == null || secretToken.isBlank()) {
            return null;
        }
        return secretToken.trim();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildSignatureHeader(String timestamp, String requestBody, String secret) throws Exception {
        String signedPayload = timestamp + "." + requestBody;
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] signatureBytes = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + toHex(signatureBytes);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
