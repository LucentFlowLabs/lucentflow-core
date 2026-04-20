package com.lucentflow.api.service;

import com.lucentflow.api.dto.ForensicEventDTO;
import com.lucentflow.api.spec.WhaleTransactionSpecifications;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;

/**
 * Read-only forensic query service backed by JPA Specifications.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class ForensicQueryService {

    private final WhaleTransactionRepository whaleTransactionRepository;

    @Transactional(readOnly = true)
    public Page<ForensicEventDTO> queryEvents(
            Integer minRiskScore,
            Integer maxRiskScore,
            String address,
            String bytecodeHash,
            String reason,
            Pageable pageable
    ) {
        Specification<WhaleTransaction> spec = Specification.where(WhaleTransactionSpecifications.minRiskScore(minRiskScore))
                .and(WhaleTransactionSpecifications.maxRiskScore(maxRiskScore))
                .and(WhaleTransactionSpecifications.address(address))
                .and(WhaleTransactionSpecifications.bytecodeHash(bytecodeHash))
                .and(WhaleTransactionSpecifications.reasonContains(reason));

        return whaleTransactionRepository.findAll(spec, pageable).map(this::toDto);
    }

    private ForensicEventDTO toDto(WhaleTransaction tx) {
        return new ForensicEventDTO(
                tx.getHash(),
                tx.getBlockNumber(),
                tx.getTimestamp(),
                tx.getFromAddress(),
                tx.getToAddress(),
                tx.getValueEth(),
                tx.getRiskScore(),
                tx.getRugRiskLevel(),
                tx.getExecutionStatus(),
                tx.getIsContractCreation(),
                tx.getBytecodeHash(),
                tx.getRiskReasons() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tx.getRiskReasons())
        );
    }
}
