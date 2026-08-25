package com.lucentflow.analyzer.worker;

import com.lucentflow.analyzer.service.AddressLabeler;
import com.lucentflow.analyzer.service.AlertService;
import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.analyzer.service.RiskEngine;
import com.lucentflow.analyzer.service.TagInferenceEngine;
import com.lucentflow.analyzer.service.TagOracleService;
import com.lucentflow.common.entity.WhaleTransaction;
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
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Alerts must not fire when the whale UPSERT fails.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WhaleAnalysisWorkerPersistThenAlertTest {

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
    void persistThenAlert_skipsAlertsWhenUpsertFails() {
        WhaleTransaction tx = sampleTx();
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(whaleDatabaseSink).saveWhaleTransactions(List.of(tx));

        assertThatThrownBy(() -> worker.persistThenAlert(List.of(tx)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(alertService, never()).sendAlertIfNeeded(tx);
    }

    @Test
    void persistThenAlert_alertsOnlyAfterSuccessfulUpsert() {
        WhaleTransaction tx = sampleTx();

        worker.persistThenAlert(List.of(tx));

        verify(whaleDatabaseSink).saveWhaleTransactions(List.of(tx));
        verify(alertService).sendAlertIfNeeded(tx);
    }

    private static WhaleTransaction sampleTx() {
        return WhaleTransaction.builder()
                .hash("0xabc")
                .fromAddress("0xfrom")
                .valueEth(BigDecimal.ONE)
                .blockNumber(1L)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .isContractCreation(false)
                .build();
    }
}
