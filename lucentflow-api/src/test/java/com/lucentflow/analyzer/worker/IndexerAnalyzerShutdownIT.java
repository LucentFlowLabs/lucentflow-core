package com.lucentflow.analyzer.worker;

import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.analyzer.service.AlertService;
import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.analyzer.service.RiskEngine;
import com.lucentflow.analyzer.service.TagInferenceEngine;
import com.lucentflow.analyzer.service.TagOracleService;
import com.lucentflow.common.lease.LeadershipGate;
import com.lucentflow.common.pipeline.TransactionPipe;
import com.lucentflow.common.repository.SyncStatusRepository;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import com.lucentflow.indexer.config.IndexerRpcProfile;
import com.lucentflow.indexer.config.RpcConcurrencyGovernor;
import com.lucentflow.indexer.control.AdaptiveBackpressureController;
import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import com.lucentflow.indexer.sink.WhaleDatabaseSink;
import com.lucentflow.indexer.source.BaseBlockSource;
import com.lucentflow.pipeline.BlockSourcePort;
import com.lucentflow.pipeline.FundingTracerPort;
import com.lucentflow.pipeline.WhaleTransactionSink;
import com.lucentflow.sdk.config.RpcEndpointState;
import com.lucentflow.sdk.config.RpcProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Full ingest SmartLifecycle: indexer (higher phase) stops accepting first, then analyzer
 * last-chance flushes the remaining pipe so {@code TransactionPipe} is empty at destroy.
 *
 * <p>Lives in this package so it can set {@link WhaleAnalysisWorker} test hooks
 * (same as {@code WhaleAnalysisWorkerShutdownTest}); compiled in {@code lucentflow-api}
 * because analyzer must not depend on indexer at compile time.</p>
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class IndexerAnalyzerShutdownIT {

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
    private PipelineOrchestrator indexer;
    private WhaleAnalysisWorker analyzer;

    @BeforeEach
    void setUp() {
        transactionPipe = new TransactionPipe();
        indexer = new PipelineOrchestrator(
                org.mockito.Mockito.mock(BaseBlockSource.class),
                org.mockito.Mockito.mock(WhaleDatabaseSink.class),
                org.mockito.Mockito.mock(SyncStatusRepository.class),
                transactionPipe,
                org.mockito.Mockito.mock(RpcProviderConfig.class),
                org.mockito.Mockito.mock(JdbcTemplate.class),
                org.mockito.Mockito.mock(RpcConcurrencyGovernor.class),
                org.mockito.Mockito.mock(AdaptiveBackpressureController.class),
                org.mockito.Mockito.mock(IndexerRpcProfile.class),
                org.mockito.Mockito.mock(RpcEndpointState.class),
                leadershipGate);
        analyzer = new WhaleAnalysisWorker(
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
        analyzer.shutdownDrainTimeoutSeconds = 0L;
        analyzer.shutdownExecutorTimeoutSeconds = 1L;
        analyzer.sinkRetrySleep = millis -> { };
    }

    @Test
    void indexerPhaseIsHigherSoProducerStopsBeforeAnalyzer() {
        assertThat(indexer.getPhase()).isGreaterThan(analyzer.getPhase());
    }

    @Test
    void stop_indexerThenAnalyzer_lastChanceFlushesPendingPipe() throws Exception {
        Transaction tx = whaleTransfer("0xshutdown-full");
        transactionPipe.push(tx, Instant.parse("2026-01-01T00:00:00Z"));
        when(blockSource.fetchTransactionReceiptAsync("0xshutdown-full"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        indexer.start();
        indexer.stop();

        assertThat(transactionPipe.hasPending()).isTrue();
        Transaction rejected = org.mockito.Mockito.mock(Transaction.class);
        when(rejected.getHash()).thenReturn("0xrejected-after-stop");
        transactionPipe.push(rejected, Instant.parse("2026-01-01T00:00:01Z"));
        assertThat(transactionPipe.size()).isEqualTo(1);

        analyzer.stop();

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
