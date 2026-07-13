package com.lucentflow.api.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates opaque project API keys and deterministic at-rest hashes.
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class ProjectApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PREFIX_LEN = 8;

    private ProjectApiKeyGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "lfproj_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hex digest used for DB lookup (never store plaintext API keys).
     */
    public static String hash(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String prefix(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String trimmed = apiKey.trim();
        return trimmed.substring(0, Math.min(PREFIX_LEN, trimmed.length()));
    }

    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 12) {
            return "****";
        }
        return apiKey.substring(0, PREFIX_LEN) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    public static String maskFromPrefix(String apiKeyPrefix) {
        if (apiKeyPrefix == null || apiKeyPrefix.isBlank()) {
            return "****";
        }
        return apiKeyPrefix.trim() + "...****";
    }
}
