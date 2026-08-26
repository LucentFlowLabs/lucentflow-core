package com.lucentflow.common.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Transaction;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransactionPipeTest {

    private TransactionPipe transactionPipe;

    @BeforeEach
    void setUp() {
        transactionPipe = new TransactionPipe();
    }

    @Test
    void shouldPushTransactionToQueue() throws InterruptedException {
        Transaction mockTx = mock(Transaction.class);
        transactionPipe.push(mockTx);
        
        assertThat(transactionPipe.size()).isEqualTo(1);
    }

    @Test
    void shouldDrainBatchSuccessfully() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            transactionPipe.push(mock(Transaction.class));
        }

        List<PipedTransaction> batch = transactionPipe.drainBatch(7);
        
        assertThat(batch).hasSize(7);
        assertThat(transactionPipe.size()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyListWhenDrainingEmptyQueue() {
        List<PipedTransaction> batch = transactionPipe.drainBatch(10);
        assertThat(batch).isEmpty();
    }

    @Test
    void shouldIncrementBackpressureEventsWhenQueueIsFull() throws Exception {
        for (int i = 0; i < 5000; i++) {
            transactionPipe.push(mock(Transaction.class));
        }

        Thread producer = Thread.startVirtualThread(() -> {
            try {
                transactionPipe.push(mock(Transaction.class));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(1500);
        assertThat(transactionPipe.getBackpressureEvents()).isGreaterThan(0);

        transactionPipe.drainBatch(1);
        producer.join(5000);
    }

    @Test
    void shouldProvideAccurateStatistics() throws InterruptedException {
        transactionPipe.push(mock(Transaction.class));
        String stats = transactionPipe.getStatistics();
        
        assertThat(stats).contains("Backpressure-Aware");
        assertThat(stats).contains("Total Enqueued: 1");
        assertThat(stats).contains("at-most-once");
        assertThat(stats).doesNotContain("Drop Rate");
        assertThat(stats).doesNotContain("zero-loss");
    }

    @Test
    void drainAll_removesEveryPendingItem() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            transactionPipe.push(mock(Transaction.class));
        }

        List<PipedTransaction> all = transactionPipe.drainAll();

        assertThat(all).hasSize(4);
        assertThat(transactionPipe.hasPending()).isFalse();
        assertThat(transactionPipe.drainAll()).isEmpty();
    }

    @Test
    void clear_afterDrainLeavesEmptyPipe() throws InterruptedException {
        transactionPipe.push(mock(Transaction.class));
        transactionPipe.stopAccepting();
        transactionPipe.drainAll();
        transactionPipe.clear();

        assertThat(transactionPipe.hasPending()).isFalse();
    }

    @Test
    void shouldStopAcceptingPushesWithoutClearingQueue() throws InterruptedException {
        transactionPipe.push(mock(Transaction.class));
        transactionPipe.stopAccepting();

        assertThat(transactionPipe.size()).isEqualTo(1);
        assertThat(transactionPipe.hasPending()).isTrue();

        transactionPipe.push(mock(Transaction.class));
        assertThat(transactionPipe.size()).isEqualTo(1);

        assertThat(transactionPipe.drainBatch(10)).hasSize(1);
        assertThat(transactionPipe.hasPending()).isFalse();
    }

    @Test
    void push_preservesBlockTimestampOnDrain() throws InterruptedException {
        Transaction mockTx = mock(Transaction.class);
        Instant blockTime = Instant.ofEpochSecond(1_672_531_200L);

        transactionPipe.push(mockTx, blockTime);

        PipedTransaction piped = transactionPipe.drainBatch(1).getFirst();
        assertThat(piped.transaction()).isSameAs(mockTx);
        assertThat(piped.blockTimestamp()).isEqualTo(blockTime);
    }
}
