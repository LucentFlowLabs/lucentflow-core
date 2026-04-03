package com.lucentflow.indexer.config;

import java.net.URI;

/**
 * Detects the official Base mainnet public JSON-RPC host ({@code mainnet.base.org}).
 * Used for "convention over configuration": safe hardcoded indexer pacing applies when the
 * effective endpoint is this host (primary or HTTP failover to backup).
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class OfficialBaseRpcPolicy {

    /**
     * Hostname for Base mainnet public RPC (https://mainnet.base.org).
     */
    public static final String OFFICIAL_MAINNET_HOST = "mainnet.base.org";

    private OfficialBaseRpcPolicy() {
    }

    /**
     * @param url full JSON-RPC URL (may include path)
     * @return {@code true} if host is {@link #OFFICIAL_MAINNET_HOST}; blank URL is treated as official-safe default
     */
    public static boolean isOfficialBasePublicRpc(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost();
            if (host == null) {
                return false;
            }
            return OFFICIAL_MAINNET_HOST.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
