package com.lucentflow.analyzer.service;

import com.lucentflow.common.repository.FundingEdgeRepository;
import com.lucentflow.indexer.service.CreatorFundingTracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for Genesis Trace 3.0 topology persistence.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class FundingTopologyServiceTest {

    @Mock
    private FundingEdgeRepository fundingEdgeRepository;

    @InjectMocks
    private FundingTopologyService fundingTopologyService;

    @Test
    void recordGenesisEdge_persistsNormalizedAddresses() {
        var outcome = new CreatorFundingTracer.GenesisTraceOutcome(
                "0xABC", "CEX", true, 2);
        fundingTopologyService.recordGenesisEdge("0xDEF", outcome, "0xhash");

        verify(fundingEdgeRepository).upsertEdge(
                eq("0xabc"),
                eq("0xdef"),
                eq(2),
                eq("0xhash"),
                eq("CEX"),
                eq(true)
        );
    }
}
