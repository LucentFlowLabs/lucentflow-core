package com.lucentflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canonical on-chain address label stored in {@code entity_tags} for O(1) oracle resolution.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "entity_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityTag {

    @Id
    @Column(name = "address", nullable = false, length = 42)
    private String address;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private EntityTagCategory category;

    @Column(name = "risk_score_modifier", nullable = false)
    @Builder.Default
    private Integer riskScoreModifier = 0;

    @Column(name = "metadata", nullable = false, length = 4000)
    @Builder.Default
    private String metadata = "{}";

    @PrePersist
    @PreUpdate
    void normalizeAddress() {
        if (address != null) {
            address = address.toLowerCase();
        }
    }
}
