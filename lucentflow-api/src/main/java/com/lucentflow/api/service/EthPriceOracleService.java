package com.lucentflow.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight ETH/USD price oracle (Phase 4 multi-asset foundation).
 * Uses CoinGecko simple price endpoint with an in-memory TTL cache.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
public class EthPriceOracleService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;
    private final AtomicReference<CachedPrice> cache = new AtomicReference<>();

    public EthPriceOracleService(
            OkHttpClient okHttpClient,
            ObjectMapper objectMapper,
            @Value("${lucentflow.oracle.enabled:true}") boolean enabled,
            @Value("${lucentflow.oracle.ttl-seconds:60}") long ttlSeconds
    ) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(Math.max(5L, ttlSeconds));
    }

    public BigDecimal ethUsd() {
        if (!enabled) {
            return null;
        }
        CachedPrice cached = cache.get();
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.price();
        }
        try {
            Request request = new Request.Builder()
                    .url("https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd")
                    .get()
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return cached == null ? null : cached.price();
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                BigDecimal price = root.path("ethereum").path("usd").decimalValue();
                if (price.signum() <= 0) {
                    return cached == null ? null : cached.price();
                }
                cache.set(new CachedPrice(price, now.plus(ttl)));
                return price;
            }
        } catch (Exception e) {
            log.debug("[ORACLE] ETH/USD fetch failed: {}", e.getMessage());
            return cached == null ? null : cached.price();
        }
    }

    private record CachedPrice(BigDecimal price, Instant expiresAt) {
    }
}
