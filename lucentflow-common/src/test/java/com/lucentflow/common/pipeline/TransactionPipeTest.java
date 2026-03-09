package com.lucentflow.common.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.Transaction;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Enterprise-grade unit tests for TransactionPipe zero-loss guarantee architecture.
 * Validates blocking queue behavior, virtual thread consumers, and fault tolerance.
 * 
 * <p>Zero-Loss Architecture Validation:</p>
 * <ul>
 *   <li>Blocking put() - Mathematical guarantee of zero transaction drops</li>
 *   <li>Virtual thread consumers - High concurrency with Java 21 features</li>
 *   <li>Fault-tolerant dispatch - Single consumer failure cannot compromise pipeline</li>
 *   <li>Backpressure handling - Queue capacity under extreme load conditions</li>
 *   <li>Thread safety - CopyOnWriteArrayList with proper synchronization</li>
 * </ul>
 * 
 * <p>Performance Characteristics:</p>
 * <ul>
 *   <li>O(1) push operations with blocking semantics</li>
 *   <li>O(1) take operations with blocking semantics</li>
 *   <li>O(n) consumer iteration where n = registered consumers</li>
 *   <li>Unbounded capacity with zero-loss mathematical guarantee</li>
 * </ul>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionPipe Zero-Loss Architecture Tests")
@Slf4j
class TransactionPipeTest {

    @Mock
    private TransactionPipe.TransactionConsumer consumer;
    
    private TransactionPipe transactionPipe;
    
    @BeforeEach
    void setUp() {
        transactionPipe = new TransactionPipe();
    }
    
    @Test
    @DisplayName("Should maintain zero-loss guarantee for single transaction")
    void shouldMaintainZeroLossGuaranteeForSingleTransaction() throws InterruptedException {
        // Given
        Transaction testTx = createMockTransaction("0x123");
        CountDownLatch consumerLatch = new CountDownLatch(1);
        AtomicInteger receivedTransactions = new AtomicInteger(0);
        
        // Configure consumer to track invocations
        doAnswer(invocation -> {
            receivedTransactions.incrementAndGet();
            consumerLatch.countDown();
            return null;
        }).when(consumer).handle(any(Transaction.class));
        
        transactionPipe.registerConsumer(consumer);
        
        // Start virtual thread consumer for queue validation
        Thread consumerThread = Thread.ofVirtual().start(() -> {
            try {
                transactionPipe.take(); // Just consume, don't need value
                log.debug("Consumer received transaction");
                consumerLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // When - Push is blocking void, reaching this line proves zero-loss success
        transactionPipe.push(testTx);
        
        // Then - Verify zero-loss guarantee: both queue and immediate dispatch
        assertThat(consumerLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedTransactions.get()).isEqualTo(1); // Immediate dispatch
        verify(consumer, times(1)).handle(testTx);
        
        // Cleanup
        consumerThread.join(1000);
    }
    
    @Test
    @DisplayName("Should handle concurrent zero-loss operations with virtual threads")
    void shouldHandleConcurrentZeroLossOperationsWithVirtualThreads() throws InterruptedException {
        // Given
        int transactionCount = 100;
        CountDownLatch allConsumedLatch = new CountDownLatch(transactionCount);
        AtomicInteger consumedCount = new AtomicInteger(0);
        
        // Configure consumer for exact invocation tracking
        doAnswer(invocation -> {
            consumedCount.incrementAndGet();
            allConsumedLatch.countDown();
            return null;
        }).when(consumer).handle(any(Transaction.class));
        
        transactionPipe.registerConsumer(consumer);
        
        // Start virtual thread consumer
        Thread consumerThread = Thread.ofVirtual().start(() -> {
            try {
                while (consumedCount.get() < transactionCount) {
                    transactionPipe.take();
                    log.debug("Virtual consumer received transaction");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // When - Push transactions via virtual threads
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < transactionCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    Transaction tx = createMockTransaction("0x" + index);
                    transactionPipe.push(tx); // Zero-loss blocking void
                });
            }
        }
        
        // Then - Verify zero-loss: all transactions consumed
        assertThat(allConsumedLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(consumedCount.get()).isEqualTo(transactionCount);
        verify(consumer, times(transactionCount)).handle(any(Transaction.class));
        
        // Cleanup
        consumerThread.join(1000);
    }
    
    @Test
    @DisplayName("Should maintain zero-loss guarantee under consumer failure")
    void shouldMaintainZeroLossUnderConsumerFailure() throws InterruptedException {
        // Given
        Transaction testTx = createMockTransaction("0x456");
        CountDownLatch queueLatch = new CountDownLatch(1);
        AtomicInteger queueReceivedCount = new AtomicInteger(0);
        
        // Configure failing consumer to test fault tolerance
        doAnswer(invocation -> {
            throw new RuntimeException("Simulated consumer failure - should not break pipeline");
        }).when(consumer).handle(any(Transaction.class));
        
        transactionPipe.registerConsumer(consumer);
        
        // Start virtual thread consumer to validate queue behavior
        Thread consumerThread = Thread.ofVirtual().start(() -> {
            try {
                transactionPipe.take(); // Just consume, don't need value
                queueReceivedCount.incrementAndGet();
                queueLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // When - Push should succeed despite consumer failure (zero-loss maintained)
        transactionPipe.push(testTx);
        
        // Then - Verify queue received transaction even with consumer failure
        assertThat(queueLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(queueReceivedCount.get()).isEqualTo(1);
        verify(consumer, times(1)).handle(testTx);
        
        // Cleanup
        consumerThread.join(1000);
    }
    
    @Test
    @DisplayName("Should handle backpressure with zero-loss guarantee")
    void shouldHandleBackpressureWithZeroLossGuarantee() throws InterruptedException {
        // Given
        int highVolumeCount = 2000;
        CountDownLatch backpressureLatch = new CountDownLatch(highVolumeCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        // Configure consumer for high-volume processing
        doAnswer(invocation -> {
            processedCount.incrementAndGet();
            backpressureLatch.countDown();
            return null;
        }).when(consumer).handle(any(Transaction.class));
        
        transactionPipe.registerConsumer(consumer);
        
        // Start multiple virtual thread consumers for backpressure testing
        Thread[] consumerThreads = new Thread[5];
        for (int i = 0; i < consumerThreads.length; i++) {
            final int threadIndex = i;
            consumerThreads[i] = Thread.ofVirtual().start(() -> {
                try {
                    while (processedCount.get() < highVolumeCount) {
                        transactionPipe.take();
                        log.debug("Consumer {} received transaction", threadIndex);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // When - Push high volume to test backpressure handling
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < highVolumeCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    Transaction tx = createMockTransaction("0xback" + index);
                    transactionPipe.push(tx); // Zero-loss under extreme load
                });
            }
        }
        
        // Then - Verify zero-loss under backpressure
        assertThat(backpressureLatch.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(processedCount.get()).isEqualTo(highVolumeCount);
        
        // Cleanup
        for (Thread thread : consumerThreads) {
            thread.join(1000);
        }
    }
    
    @Test
    @DisplayName("Should provide accurate zero-loss statistics")
    void shouldProvideAccurateZeroLossStatistics() {
        // Given
        int transactionVolume = 500;
        for (int i = 0; i < transactionVolume; i++) {
            Transaction tx = createMockTransaction("0xstat" + i);
            transactionPipe.push(tx); // Each push proves zero-loss operation
        }
        
        // When
        String stats = transactionPipe.getStatistics();
        
        // Then - Zero-loss metrics must be present and accurate
        assertThat(stats).isNotNull();
        assertThat(stats).contains("TransactionPipe Statistics (Zero-Loss):");
        assertThat(stats).contains("Current Size: " + transactionVolume);
        assertThat(stats).contains("Total Processed: " + transactionVolume);
        assertThat(stats).contains("Drop Rate: 0.00%"); // Critical zero-loss indicator
        assertThat(stats).contains("Registered Consumers: 0"); // No consumers registered
    }
    
    @Test
    @DisplayName("Should reject null transactions with proper error handling")
    void shouldRejectNullTransactionsWithProperErrorHandling() {
        // When & Then - Should throw IllegalArgumentException with precise message
        try {
            transactionPipe.push(null);
            assertThat(false).as("Expected IllegalArgumentException for null transaction").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Transaction cannot be null");
        }
    }
    
    @Test
    @DisplayName("Should handle thread interruption gracefully")
    void shouldHandleThreadInterruptionGracefully() throws InterruptedException {
        // Given
        CountDownLatch interruptLatch = new CountDownLatch(1);
        
        // Start virtual thread consumer
        Thread consumerThread = Thread.ofVirtual().start(() -> {
            try {
                transactionPipe.take();
            } catch (InterruptedException e) {
                log.debug("Consumer thread interrupted as expected");
                Thread.currentThread().interrupt();
                interruptLatch.countDown();
            }
        });
        
        // When - Interrupt consumer thread
        consumerThread.interrupt();
        
        // Then - Should handle interruption gracefully
        assertThat(interruptLatch.await(5, TimeUnit.SECONDS)).isTrue();
        
        // Cleanup
        consumerThread.join(1000);
    }
    
    /**
     * Factory method to create mock transaction with realistic blockchain data.
     * 
     * @param hash Transaction hash (must be unique for test isolation)
     * @return Configured Transaction instance with complete blockchain metadata
     */
    private Transaction createMockTransaction(String hash) {
        Transaction tx = new Transaction();
        tx.setHash(hash);
        tx.setFrom("0xfrom1234567890123456789012345678901234567890");
        tx.setTo("0xto1234567890123456789012345678901234567890");
        tx.setValue("1000000000000000000"); // 1 ETH in wei
        tx.setGas("21000");
        tx.setGasPrice("20000000000");
        tx.setNonce("1");
        tx.setBlockNumber("12345");
        tx.setTransactionIndex("0");
        tx.setInput("0xinputdata123456789");
        tx.setV("0x1");
        tx.setR("0x2");
        tx.setS("0x3");
        return tx;
    }
}
