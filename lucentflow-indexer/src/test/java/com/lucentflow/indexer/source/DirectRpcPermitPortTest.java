package com.lucentflow.indexer.source;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author ArchLucent
 * @since 1.2
 */
class DirectRpcPermitPortTest {

    @Test
    void runsActionWithoutWrappingRuntimeException() {
        DirectRpcPermitPort port = new DirectRpcPermitPort();
        AtomicInteger calls = new AtomicInteger();
        Integer result = port.runWithRpcPermit(() -> {
            calls.incrementAndGet();
            return 7;
        });
        assertEquals(7, result);
        assertEquals(1, calls.get());
    }

    @Test
    void wrapsCheckedException() {
        DirectRpcPermitPort port = new DirectRpcPermitPort();
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> port.runWithRpcPermit(() -> {
                    throw new Exception("rpc-down");
                }));
        assertEquals("RPC call failed", thrown.getMessage());
        assertEquals("rpc-down", thrown.getCause().getMessage());
    }
}
