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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Catch-up lag uses one sample: skip low-value / low-score enrich, fail-open on RPC errors.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WhaleAnalysisWorkerCatchUpTest {

    @Mock
    private TransactionPipe transactionPipe;
    @Mock
    private AddressLabeler addressLabeler;
    @Mock
    private WhaleTransactionSink whaleDatabaseSink;
    @Mock
    private FundingTracerPort creatorFundingTracer;
    @Mock
    private RiskEngine riskEngine;
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

    @InjectMocks
    private WhaleAnalysisWorker worker;

    @Test
    void isCatchUpMode_trueWhenLagExceedsDefaultThreshold() {
        when(blockSource.getLastScannedBlock()).thenReturn(1_000L);
        when(blockSource.getLatestBlockNumber()).thenReturn(1_501L);

        assertThat(worker.isCatchUpMode()).isTrue();
        assertThat(worker.shouldSkipDeepOriginTrace(40)).isTrue();
        assertThat(worker.shouldSkipTracingInCatchUp(new BigDecimal("10"))).isTrue();
    }

    @Test
    void isCatchUpMode_falseWhenLagAtOrBelowThreshold() {
        when(blockSource.getLastScannedBlock()).thenReturn(1_000L);
        when(blockSource.getLatestBlockNumber()).thenReturn(1_500L);

        assertThat(worker.isCatchUpMode()).isFalse();
        assertThat(worker.shouldSkipDeepOriginTrace(40)).isFalse();
        assertThat(worker.shouldSkipTracingInCatchUp(new BigDecimal("10"))).isFalse();
    }

    @Test
    void isCatchUpMode_failOpenWhenLagCannotBeRead() {
        when(blockSource.getLastScannedBlock()).thenThrow(new IllegalStateException("rpc down"));

        assertThat(worker.isCatchUpMode()).isFalse();
        assertThat(worker.shouldSkipDeepOriginTrace(40)).isFalse();
        assertThat(worker.shouldSkipTracingInCatchUp(new BigDecimal("10"))).isFalse();
    }

    @Test
    void shouldSkipDeepOriginTrace_highScoreStillTracesDuringCatchUp() {
        when(blockSource.getLastScannedBlock()).thenReturn(1_000L);
        when(blockSource.getLatestBlockNumber()).thenReturn(2_000L);

        assertThat(worker.isCatchUpMode()).isTrue();
        assertThat(worker.shouldSkipDeepOriginTrace(59)).isTrue();
        assertThat(worker.shouldSkipDeepOriginTrace(60)).isFalse();
    }

    @Test
    void shouldSkipTracingInCatchUp_largeTransfersNeverSkip() {
        assertThat(worker.shouldSkipTracingInCatchUp(new BigDecimal("20"))).isFalse();
        assertThat(worker.shouldSkipTracingInCatchUp(null)).isFalse();
    }
}
