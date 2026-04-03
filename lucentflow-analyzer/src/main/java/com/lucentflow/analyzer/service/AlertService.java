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
 * Sends high-risk whale alerts to Telegram via Bot API ({@code sendMessage}), using the shared
 * {@link OkHttpClient} (including optional {@code PROXY_HOST}/{@code PROXY_PORT}).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class AlertService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final ExecutorService VIRTUAL_ALERT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final OkHttpClient okHttpClient;
    private final String botToken;
    private final String chatId;

    public AlertService(
            OkHttpClient okHttpClient,
            @Value("${lucentflow.telegram.bot-token:}") String botToken,
            @Value("${lucentflow.telegram.chat-id:}") String chatId) {
        this.okHttpClient = okHttpClient;
        this.botToken = botToken;
        this.chatId = chatId;
    }

    /**
     * Fire-and-forget alert on a virtual thread; never blocks the indexer pipeline.
     */
    public void sendHighRiskAlertAsync(WhaleTransaction tx) {
        if (tx == null) {
            return;
        }
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> sendMessageBlocking(tx), VIRTUAL_ALERT_EXECUTOR);
    }

    private void sendMessageBlocking(WhaleTransaction tx) {
        String url = "https://api.telegram.org/bot" + botToken.trim() + "/sendMessage";
        String bodyJson = buildSendMessageJson(chatId.trim(), formatMessage(tx));
        RequestBody body = RequestBody.create(bodyJson, JSON);
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("[TELEGRAM] sendMessage failed: HTTP {} body={}", response.code(), truncate(errBody, 2000));
            }
        } catch (Exception e) {
            log.warn("[TELEGRAM] sendMessage I/O error: {}", e.getMessage());
        }
    }

    private static String buildSendMessageJson(String chatIdValue, String htmlText) {
        return "{"
                + "\"chat_id\":" + jsonString(chatIdValue) + ","
                + "\"text\":" + jsonString(htmlText) + ","
                + "\"parse_mode\":\"HTML\","
                + "\"disable_web_page_preview\":false"
                + "}";
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        String escaped = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    /**
     * HTML body for Telegram {@code parse_mode=HTML}; dynamic fields are escaped.
     */
    private String formatMessage(WhaleTransaction tx) {
        int riskScore = tx.getRiskScore() != null ? tx.getRiskScore() : 0;
        String riskStatus = Objects.requireNonNullElse(tx.getRugRiskLevel(), mapScoreToStatus(riskScore));
        String reasons = escapeHtml(tx.getRiskReasons() != null ? tx.getRiskReasons() : "—");
        String valueEth = tx.getValueEth() != null ? tx.getValueEth().stripTrailingZeros().toPlainString() : "0";
        String from = escapeHtml(tx.getFromAddress() != null ? tx.getFromAddress() : "—");
        String hash = tx.getHash() != null ? tx.getHash() : "";
        String txLink = "https://basescan.org/tx/" + hash;

        return """
                <b>🚨 [LucentFlow Security Sentinel]</b>
                --------------------------------
                <b>Risk Score:</b> %d/100 (%s)
                <b>Audit Detail:</b> %s
                <b>Value:</b> %s ETH
                <b>Initiator:</b> <code>%s</code>
                <b>Action:</b> <a href="%s">Verify on Basescan</a>
                """.formatted(riskScore, escapeHtml(riskStatus), reasons, valueEth, from, txLink);
    }

    private static String mapScoreToStatus(int score) {
        if (score <= 30) {
            return "LOW";
        }
        if (score <= 60) {
            return "MEDIUM";
        }
        if (score <= 80) {
            return "HIGH";
        }
        return "CRITICAL";
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }
}
