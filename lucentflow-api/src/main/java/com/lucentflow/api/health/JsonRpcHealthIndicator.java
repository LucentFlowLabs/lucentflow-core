package com.lucentflow.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Contributes RPC reachability to {@code /actuator/health} so operators can distinguish
 * database-up from JSON-RPC-up. Never throws: all failures become {@link Health#down()}
 * so the actuator layer can still serialize the response (HTTP status is configured separately).
 * Includes {@code PROXY_HOST}/{@code PROXY_PORT} on failure paths so timeouts can be correlated
 * with outbound proxy misconfiguration.
 *
 * @author ArchLucent
 * @since 1.1
 */
@Component
public class JsonRpcHealthIndicator implements HealthIndicator {

    private static final int RPC_TIMEOUT_SECONDS = 10;

    private final Web3j web3j;
    private final String proxyHost;
    private final String proxyPort;

    public JsonRpcHealthIndicator(
            Web3j web3j,
            @Value("${PROXY_HOST:}") String proxyHost,
            @Value("${PROXY_PORT:}") String proxyPort) {
        this.web3j = web3j;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    @Override
    public Health health() {
        try {
            EthBlockNumber ethBlockNumber = web3j.ethBlockNumber()
                    .sendAsync()
                    .get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (ethBlockNumber == null) {
                return downWithProxy()
                        .withDetail("reason", "null response")
                        .withDetail("rpc", "unreachable")
                        .build();
            }

            if (ethBlockNumber.hasError()) {
                Response.Error err = ethBlockNumber.getError();
                String code = err != null ? String.valueOf(err.getCode()) : "unknown";
                String message = err != null && err.getMessage() != null ? err.getMessage() : "unknown error";
                return downWithProxy()
                        .withDetail("rpc", "error")
                        .withDetail("code", code)
                        .withDetail("message", message)
                        .build();
            }

            if (ethBlockNumber.getBlockNumber() == null) {
                return downWithProxy()
                        .withDetail("reason", "null response")
                        .withDetail("rpc", "no-block-number")
                        .build();
            }

            return Health.up()
                    .withDetail("rpc", "reachable")
                    .withDetail("blockNumber", ethBlockNumber.getBlockNumber())
                    .build();
        } catch (TimeoutException e) {
            return downWithProxy()
                    .withDetail("rpc", "timeout")
                    .withDetail("reason", "eth_blockNumber exceeded " + RPC_TIMEOUT_SECONDS + "s")
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", safeMessage(e))
                    .build();
        } catch (Exception e) {
            return downFromThrowable("unreachable", e);
        } catch (Throwable t) {
            return downWithProxy()
                    .withDetail("rpc", "error")
                    .withDetail("reason", "unexpected throwable")
                    .withDetail("error", t.getClass().getSimpleName())
                    .withDetail("message", safeMessage(t))
                    .build();
        }
    }

    private Health.Builder downWithProxy() {
        return Health.down().withDetail("proxyOutbound", formatProxyOutboundStatus());
    }

    /**
     * Human-readable proxy line for health JSON: {@code unset}, {@code host:port}, or {@code incomplete}.
     */
    private String formatProxyOutboundStatus() {
        String h = proxyHost != null ? proxyHost.trim() : "";
        String p = proxyPort != null ? proxyPort.trim() : "";
        if (h.isEmpty() && p.isEmpty()) {
            return "unset";
        }
        if (h.isEmpty() || p.isEmpty()) {
            return "incomplete (PROXY_HOST='" + h + "' PROXY_PORT='" + p + "')";
        }
        return h + ":" + p;
    }

    private Health downFromThrowable(String rpcDetail, Throwable e) {
        Throwable root = e;
        if (e != null) {
            String msg = safeMessage(e);
            if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("too many requests"))) {
                return downWithProxy()
                        .withDetail("rpc", "rate-limited")
                        .withDetail("reason", "http 429 or provider throttle")
                        .withDetail("error", e.getClass().getSimpleName())
                        .withDetail("message", msg)
                        .build();
            }
        }
        return downWithProxy()
                .withDetail("rpc", rpcDetail)
                .withDetail("error", root != null ? root.getClass().getSimpleName() : "unknown")
                .withDetail("message", safeMessage(root))
                .build();
    }

    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "";
        }
        try {
            String m = t.getMessage();
            return m != null ? m : "";
        } catch (Throwable ignored) {
            return "(message unavailable)";
        }
    }
}
