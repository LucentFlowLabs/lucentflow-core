package com.lucentflow.api.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

import java.util.concurrent.TimeUnit;

/**
 * Contributes RPC reachability to {@code /actuator/health} so operators can distinguish
 * database-up from JSON-RPC-up.
 *
 * @author ArchLucent
 * @since 1.1
 */
@Component
public class JsonRpcHealthIndicator implements HealthIndicator {

    private static final int RPC_TIMEOUT_SECONDS = 10;

    private final Web3j web3j;

    public JsonRpcHealthIndicator(Web3j web3j) {
        this.web3j = web3j;
    }

    @Override
    public Health health() {
        try {
            EthBlockNumber ethBlockNumber = web3j.ethBlockNumber()
                    .sendAsync()
                    .get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (ethBlockNumber.hasError()) {
                return Health.down()
                        .withDetail("rpc", "error")
                        .withDetail("message", ethBlockNumber.getError().getMessage())
                        .build();
            }
            return Health.up()
                    .withDetail("rpc", "reachable")
                    .withDetail("blockNumber", ethBlockNumber.getBlockNumber()).build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("rpc", "unreachable")
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
