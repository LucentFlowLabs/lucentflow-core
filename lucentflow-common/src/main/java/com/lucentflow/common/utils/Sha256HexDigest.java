package com.lucentflow.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest for UTF-8 strings (e.g. contract creation {@code input} data).
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class Sha256HexDigest {

    private Sha256HexDigest() {
    }

    /**
     * @param input raw hex string from Web3j transaction input (may be null/blank)
     * @return 64-character lowercase hex, or null if input is null/blank
     */
    public static String hashUtf8(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
