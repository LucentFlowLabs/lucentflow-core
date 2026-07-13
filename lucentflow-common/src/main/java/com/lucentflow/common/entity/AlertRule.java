package com.lucentflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-project alert routing and threshold configuration.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(name = "min_risk_score", nullable = false)
    private Integer minRiskScore;

    @Column(name = "watchlist_only", nullable = false)
    private Boolean watchlistOnly;

    @Column(name = "contract_creation_only", nullable = false)
    private Boolean contractCreationOnly;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (minRiskScore == null) {
            minRiskScore = 70;
        }
        if (watchlistOnly == null) {
            watchlistOnly = Boolean.FALSE;
        }
        if (contractCreationOnly == null) {
            contractCreationOnly = Boolean.FALSE;
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
