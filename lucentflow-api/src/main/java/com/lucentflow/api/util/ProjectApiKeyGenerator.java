package com.lucentflow.api.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque project API keys.
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class ProjectApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ProjectApiKeyGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "lfproj_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 12) {
            return "****";
        }
        return apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
