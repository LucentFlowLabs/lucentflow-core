package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.WhaleTransaction;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discord webhook alert channel provider (Phase 4 multi-channel alerting).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class DiscordAlertProvider implements AlertProvider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final OkHttpClient okHttpClient;
    private final String webhookUrl;

    public DiscordAlertProvider(
            OkHttpClient okHttpClient,
            @Value("${lucentflow.discord.webhook-url:}") String webhookUrl) {
        this.okHttpClient = okHttpClient;
        this.webhookUrl = webhookUrl;
    }

    /**
     * Operator-global Discord webhook from env. Not a per-project channel.
     */
    @Override
    public boolean supportsProjectScopedDispatch() {
        return false;
    }

    @Override
    public void sendHighRiskAlertAsync(WhaleTransaction tx, AlertDispatchContext context) {
        if (tx == null || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> sendBlocking(tx, context), EXECUTOR);
    }

    private void sendBlocking(WhaleTransaction tx, AlertDispatchContext context) {
        String payload = "{\"content\":" + jsonString(formatMessage(tx, context)) + "}";
        RequestBody body = RequestBody.create(payload, JSON);
        Request request = new Request.Builder().url(webhookUrl.trim()).post(body).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("[DISCORD] webhook failed: HTTP {} body={}", response.code(), truncate(errBody, 500));
            }
        } catch (Exception e) {
            log.warn("[DISCORD] webhook I/O error: {}", e.getMessage());
        }
    }

    private String formatMessage(WhaleTransaction tx, AlertDispatchContext context) {
        int riskScore = tx.getRiskScore() != null ? tx.getRiskScore() : 0;
        String riskStatus = Objects.requireNonNullElse(tx.getRugRiskLevel(), "UNKNOWN");
        String valueEth = tx.getValueEth() != null ? tx.getValueEth().stripTrailingZeros().toPlainString() : "0";
        String hash = tx.getHash() == null ? "" : tx.getHash();
        String watchlist = context != null && context.watchlistHit()
                ? "YES (" + nullToDash(context.watchlistLabel()) + ")"
                : "NO";
        return """
                **LucentFlow Security Sentinel**
                Risk: **%d/100 (%s)**
                Watchlist: %s
                Value: %s ETH
                From: `%s`
                Tx: https://basescan.org/tx/%s
                """.formatted(
                riskScore,
                riskStatus,
                watchlist,
                valueEth,
                nullToDash(tx.getFromAddress()),
                hash
        );
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
