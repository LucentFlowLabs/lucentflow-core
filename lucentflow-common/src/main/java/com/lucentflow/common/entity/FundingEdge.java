package com.lucentflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Directed funding edge for Genesis Trace 3.0 topology queries.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "funding_edges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funder_address", nullable = false, length = 42)
    private String funderAddress;

    @Column(name = "funded_address", nullable = false, length = 42)
    private String fundedAddress;

    @Column(name = "hop_layer", nullable = false)
    private Integer hopLayer;

    @Column(name = "related_tx_hash", length = 66)
    private String relatedTxHash;

    @Column(name = "funder_tag", length = 120)
    private String funderTag;

    @Column(name = "blacklisted", nullable = false)
    private Boolean blacklisted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (blacklisted == null) {
            blacklisted = Boolean.FALSE;
        }
        if (hopLayer == null) {
            hopLayer = 1;
        }
        if (funderAddress != null) {
            funderAddress = funderAddress.trim().toLowerCase();
        }
        if (fundedAddress != null) {
            fundedAddress = fundedAddress.trim().toLowerCase();
        }
    }
}
