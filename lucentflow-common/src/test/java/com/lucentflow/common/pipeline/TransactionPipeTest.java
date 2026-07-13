package com.lucentflow.common.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Transaction;

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

        List<Transaction> batch = transactionPipe.drainBatch(7);
        
        assertThat(batch).hasSize(7);
        assertThat(transactionPipe.size()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyListWhenDrainingEmptyQueue() {
        List<Transaction> batch = transactionPipe.drainBatch(10);
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
        assertThat(stats).contains("Total Processed: 1");
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
}
