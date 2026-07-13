package com.lucentflow.common.repository;

import com.lucentflow.common.entity.FundingEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for Genesis Trace 3.0 funding topology edges.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface FundingEdgeRepository extends JpaRepository<FundingEdge, Long> {

    List<FundingEdge> findTop50ByFundedAddressOrderByCreatedAtDesc(String fundedAddress);

    List<FundingEdge> findTop50ByFunderAddressOrderByCreatedAtDesc(String funderAddress);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO funding_edges (funder_address, funded_address, hop_layer, related_tx_hash, funder_tag, blacklisted, created_at)
            VALUES (:funder, :funded, :hop, :txHash, :tag, :blacklisted, CURRENT_TIMESTAMP)
            ON CONFLICT (funder_address, funded_address, hop_layer) DO NOTHING
            """, nativeQuery = true)
    void upsertEdge(
            @Param("funder") String funder,
            @Param("funded") String funded,
            @Param("hop") int hop,
            @Param("txHash") String txHash,
            @Param("tag") String tag,
            @Param("blacklisted") boolean blacklisted
    );
}
