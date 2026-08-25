package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.RiskEngine;
import com.lucentflow.api.dto.RiskScoreRequest;
import com.lucentflow.api.dto.RiskScoreResponse;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import com.lucentflow.pipeline.FundingTracerPort;
import com.lucentflow.pipeline.GenesisTraceOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * On-demand risk score: indexed path, clone fixture, timeout, and short cache.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class RiskScoreServiceTest {

    private static final String CASE_001_DEPLOYER = "0x6ac359924348dd492a7751af122d781db984b70a";
    private static final String CASE_002_BYTECODE =
            "87192e36234d9184a43f740488a3a0c663e86a192e001cbabde48f000c0a1511";

    @Mock
    private FundingTracerPort fundingTracer;
    @Mock
    private WhaleTransactionRepository whaleTransactionRepository;

    private RiskScoreService service;

    @AfterEach
    void shutdown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void score_requiresAddressOrTxHash() {
        service = newService(8_000, 60_000);
        assertThatThrownBy(() -> service.score(new RiskScoreRequest("  ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("address or txHash");
    }

    @Test
    void score_indexedCase001Deployer_clampsAndVersions() {
        WhaleTransaction row = indexedRow(CASE_001_DEPLOYER, null, true, 90);
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                CASE_001_DEPLOYER, CASE_001_DEPLOYER)).thenReturn(Optional.of(row));
        when(whaleTransactionRepository.countRecentDeployments(anyString(), any())).thenReturn(0L);
        when(fundingTracer.traceOriginAsync(CASE_001_DEPLOYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service = newService(8_000, 60_000);

        RiskScoreResponse response = service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));

        assertThat(response.riskModelVersion()).isEqualTo(RiskEngine.MODEL_VERSION);
        assertThat(response.score()).isBetween(0, 100);
        assertThat(response.coverage()).isEqualTo(RiskScoreService.COVERAGE_INDEXED);
        assertThat(response.address()).isEqualTo(CASE_001_DEPLOYER);
        assertThat(response.reasons()).containsKey("CONTRACT_CREATION");
        assertThat(response.score()).isNotEqualTo(90);
        assertThat(response.score()).isEqualTo(30);
    }

    @Test
    void score_case002BytecodeClone_addsCloneReason() {
        WhaleTransaction row = indexedRow(CASE_001_DEPLOYER, CASE_002_BYTECODE, true, 40);
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                CASE_001_DEPLOYER, CASE_001_DEPLOYER)).thenReturn(Optional.of(row));
        when(whaleTransactionRepository.countRecentDeployments(anyString(), any())).thenReturn(5L);
        when(whaleTransactionRepository.countByBytecodeHashSince(eq(CASE_002_BYTECODE), any())).thenReturn(12L);
        when(fundingTracer.traceOriginAsync(CASE_001_DEPLOYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(
                        new GenesisTraceOutcome("0x1111111111111111111111111111111111111111",
                                "MIXER_FUNDING", true, 3))));
        service = newService(8_000, 60_000);

        RiskScoreResponse response = service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));

        assertThat(response.bytecodeHash()).isEqualTo(CASE_002_BYTECODE);
        assertThat(response.cloneCount()).isEqualTo(12L);
        assertThat(response.fundingHops()).isEqualTo(3);
        assertThat(response.blacklistedFunding()).isTrue();
        assertThat(response.reasons()).containsKeys("CONTRACT_CLONE", "BLACKLISTED_FUNDING_SOURCE");
        assertThat(response.score()).isEqualTo(RiskEngine.clampScore(response.rawScore()));
        assertThat(response.rawScore()).isGreaterThan(response.score());
    }

    @Test
    void score_timesOutWhenIndexedLookupBlocks() {
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(400);
                    return Optional.empty();
                });
        service = newService(50, 60_000);

        assertThatThrownBy(() -> service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null)))
                .isInstanceOf(RiskScoreTimeoutException.class);
    }

    @Test
    void score_cachesByAddressWithinTtl() {
        WhaleTransaction row = indexedRow(CASE_001_DEPLOYER, null, false, 10);
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                CASE_001_DEPLOYER, CASE_001_DEPLOYER)).thenReturn(Optional.of(row));
        when(whaleTransactionRepository.countRecentDeployments(anyString(), any())).thenReturn(0L);
        when(fundingTracer.traceOriginAsync(CASE_001_DEPLOYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service = newService(8_000, 60_000);

        service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));
        service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));

        verify(whaleTransactionRepository, times(1))
                .findFirstByFromAddressOrToAddressOrderByTimestampDesc(CASE_001_DEPLOYER, CASE_001_DEPLOYER);
    }

    @Test
    void score_cacheExpiresAfterTtl() throws Exception {
        WhaleTransaction row = indexedRow(CASE_001_DEPLOYER, null, false, 10);
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                CASE_001_DEPLOYER, CASE_001_DEPLOYER)).thenReturn(Optional.of(row));
        when(whaleTransactionRepository.countRecentDeployments(anyString(), any())).thenReturn(0L);
        when(fundingTracer.traceOriginAsync(CASE_001_DEPLOYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service = newService(8_000, 15);

        service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));
        Thread.sleep(40);
        service.cleanUpCache();
        service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));

        verify(whaleTransactionRepository, times(2))
                .findFirstByFromAddressOrToAddressOrderByTimestampDesc(CASE_001_DEPLOYER, CASE_001_DEPLOYER);
    }

    @Test
    void score_cacheEvictsWhenOverMaxSize() {
        String other = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        WhaleTransaction first = indexedRow(CASE_001_DEPLOYER, null, false, 10);
        WhaleTransaction second = indexedRow(other, null, false, 10);
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                CASE_001_DEPLOYER, CASE_001_DEPLOYER)).thenReturn(Optional.of(first));
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                other, other)).thenReturn(Optional.of(second));
        when(whaleTransactionRepository.countRecentDeployments(anyString(), any())).thenReturn(0L);
        when(fundingTracer.traceOriginAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service = newService(8_000, 60_000, 1);

        service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));
        service.score(new RiskScoreRequest(other, null));
        service.cleanUpCache();
        assertThat(service.cacheSize()).isLessThanOrEqualTo(1L);
        service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));

        verify(whaleTransactionRepository, times(2))
                .findFirstByFromAddressOrToAddressOrderByTimestampDesc(CASE_001_DEPLOYER, CASE_001_DEPLOYER);
    }

    @Test
    void score_unknownWhenNothingIndexedAndNoRpc() {
        when(whaleTransactionRepository.findFirstByFromAddressOrToAddressOrderByTimestampDesc(
                CASE_001_DEPLOYER, CASE_001_DEPLOYER)).thenReturn(Optional.empty());
        when(whaleTransactionRepository.countRecentDeployments(anyString(), any())).thenReturn(0L);
        when(fundingTracer.traceOriginAsync(CASE_001_DEPLOYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service = newService(8_000, 60_000);

        RiskScoreResponse response = service.score(new RiskScoreRequest(CASE_001_DEPLOYER, null));
        assertThat(response.coverage()).isEqualTo(RiskScoreService.COVERAGE_UNKNOWN);
        assertThat(response.score()).isBetween(0, 100);
    }

    private RiskScoreService newService(long timeoutMs, long cacheTtlMs) {
        return newService(timeoutMs, cacheTtlMs, 256);
    }

    private RiskScoreService newService(long timeoutMs, long cacheTtlMs, int cacheMaxSize) {
        return new RiskScoreService(
                new RiskEngine(),
                fundingTracer,
                whaleTransactionRepository,
                null,
                4,
                timeoutMs,
                cacheTtlMs,
                cacheMaxSize
        );
    }

    private static WhaleTransaction indexedRow(String from, String bytecodeHash, boolean creation, int unusedScore) {
        WhaleTransaction tx = new WhaleTransaction();
        tx.setHash("0xabc0000000000000000000000000000000000000000000000000000000000001");
        tx.setFromAddress(from);
        tx.setToAddress(creation ? null : "0xdef0000000000000000000000000000000000001");
        tx.setBlockNumber(19_000_000L);
        tx.setIsContractCreation(creation);
        tx.setBytecodeHash(bytecodeHash);
        tx.setRugRiskLevel("LOW");
        tx.setTimestamp(Instant.parse("2026-01-15T00:00:00Z"));
        tx.setRiskScore(unusedScore);
        return tx;
    }
}
