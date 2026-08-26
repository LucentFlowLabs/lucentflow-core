package com.lucentflow.analyzer.worker;

import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.analyzer.service.AlertService;
import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.analyzer.service.RiskEngine;
import com.lucentflow.analyzer.service.TagInferenceEngine;
import com.lucentflow.analyzer.service.TagOracleService;
import com.lucentflow.common.lease.LeadershipGate;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import com.lucentflow.pipeline.BlockSourcePort;
import com.lucentflow.pipeline.FundingTracerPort;
import com.lucentflow.pipeline.WhaleTransactionSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Shutdown last-chance flush persists remaining pipe items instead of dropping them.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WhaleAnalysisWorkerShutdownTest {

    @Mock
    private WhaleTransactionSink whaleDatabaseSink;
    @Mock
    private FundingTracerPort creatorFundingTracer;
    @Mock
    private BlockSourcePort blockSource;
    @Mock
    private WhaleTransactionRepository whaleTransactionRepository;
    @Mock
    private AlertService alertService;
    @Mock
    private TagOracleService tagOracleService;
    @Mock
    private TagInferenceEngine tagInferenceEngine;
    @Mock
    private FundingTopologyService fundingTopologyService;
    @Mock
    private LeadershipGate leadershipGate;

    private TransactionPipe transactionPipe;
    private WhaleAnalysisWorker worker;

    @BeforeEach
    void setUp() {
        transactionPipe = new TransactionPipe();
        worker = new WhaleAnalysisWorker(
                transactionPipe,
                new AddressLabeler(),
                whaleDatabaseSink,
                creatorFundingTracer,
                new RiskEngine(),
                blockSource,
                whaleTransactionRepository,
                alertService,
                tagOracleService,
                tagInferenceEngine,
                fundingTopologyService,
                leadershipGate);
        worker.shutdownDrainTimeoutSeconds = 0L;
        worker.shutdownExecutorTimeoutSeconds = 1L;
        worker.sinkRetrySleep = millis -> { };
    }

    @Test
    void stop_lastChanceFlushesPendingPipeItems() throws Exception {
        Transaction tx = whaleTransfer("0xshutdown");
        transactionPipe.push(tx, Instant.parse("2026-01-01T00:00:00Z"));
        when(blockSource.fetchTransactionReceiptAsync("0xshutdown"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        worker.stop();

        verify(whaleDatabaseSink).saveWhaleTransactions(anyList());
        assertThat(transactionPipe.hasPending()).isFalse();
    }

    private static Transaction whaleTransfer(String hash) {
        Transaction tx = org.mockito.Mockito.mock(Transaction.class);
        when(tx.getHash()).thenReturn(hash);
        when(tx.getFrom()).thenReturn("0xfrom");
        when(tx.getTo()).thenReturn("0xto");
        when(tx.getValue()).thenReturn(new BigInteger("10000000000000000000"));
        when(tx.getBlockNumber()).thenReturn(BigInteger.ONE);
        when(tx.getGasPrice()).thenReturn(BigInteger.TEN);
        when(tx.getInput()).thenReturn("0x");
        return tx;
    }
}
