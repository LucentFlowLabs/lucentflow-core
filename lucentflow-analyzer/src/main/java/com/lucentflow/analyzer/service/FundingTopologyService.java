package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.FundingEdge;
import com.lucentflow.common.repository.FundingEdgeRepository;
import com.lucentflow.pipeline.GenesisTraceOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Persists and queries Genesis Trace 3.0 funding topology edges.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundingTopologyService {

    private final FundingEdgeRepository fundingEdgeRepository;

    @Transactional
    public void recordGenesisEdge(
            String fundedAddress,
            GenesisTraceOutcome outcome,
            String relatedTxHash
    ) {
        if (fundedAddress == null || fundedAddress.isBlank() || outcome == null
                || outcome.fundingSourceAddress() == null || outcome.fundingSourceAddress().isBlank()) {
            return;
        }
        try {
            fundingEdgeRepository.upsertEdge(
                    normalize(outcome.fundingSourceAddress()),
                    normalize(fundedAddress),
                    Math.max(1, outcome.layersTraced()),
                    relatedTxHash,
                    outcome.fundingSourceTag(),
                    outcome.blacklisted()
            );
        } catch (Exception e) {
            log.warn("[TOPOLOGY] Failed to persist funding edge funded={} err={}", fundedAddress, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<FundingEdge> inbound(String address) {
        return fundingEdgeRepository.findTop50ByFundedAddressOrderByCreatedAtDesc(normalize(address));
    }

    @Transactional(readOnly = true)
    public List<FundingEdge> outbound(String address) {
        return fundingEdgeRepository.findTop50ByFunderAddressOrderByCreatedAtDesc(normalize(address));
    }

    private static String normalize(String address) {
        return address == null ? null : address.trim().toLowerCase(Locale.ROOT);
    }
}
