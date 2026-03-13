package com.lucentflow.common.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.Transaction;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
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
    void shouldProvideAccurateStatistics() throws InterruptedException {
        transactionPipe.push(mock(Transaction.class));
        String stats = transactionPipe.getStatistics();
        
        assertThat(stats).contains("Backpressure-Aware");
        assertThat(stats).contains("Total Processed: 1");
    }

    @Test
    void shouldHandleNullTransactionGracefully() throws InterruptedException {
        // The current implementation returns null silently, so we verify no exception is thrown
        transactionPipe.push(null);
        
        assertThat(transactionPipe.size()).isEqualTo(0);
    }
}
