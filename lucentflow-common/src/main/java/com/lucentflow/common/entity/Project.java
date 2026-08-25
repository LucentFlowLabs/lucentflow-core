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

import com.lucentflow.common.plan.ProjectPlan;

import java.time.Instant;

/**
 * Lightweight B2B project scope used for multi-project isolation.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * SHA-256 hex of the project API key (plaintext is never persisted).
     */
    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    /**
     * First characters of the plaintext key for masked display only.
     */
    @Column(name = "api_key_prefix", nullable = false, length = 16)
    private String apiKeyPrefix;

    @Column(name = "webhook_url", length = 1024)
    private String webhookUrl;

    /**
     * Per-project HMAC secret for {@code X-LucentFlow-Signature}. Null falls back to global token.
     */
    @Column(name = "webhook_secret", length = 256)
    private String webhookSecret;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "plan", nullable = false, length = 32)
    @Builder.Default
    private String plan = ProjectPlan.BUILDER;

    @Column(name = "daily_request_quota", nullable = false)
    @Builder.Default
    private Integer dailyRequestQuota = 2_000;

    @Column(name = "watchlist_limit", nullable = false)
    @Builder.Default
    private Integer watchlistLimit = 50;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
        if (plan == null || plan.isBlank()) {
            plan = ProjectPlan.BUILDER;
        }
        if (dailyRequestQuota == null) {
            dailyRequestQuota = ProjectPlan.defaultDailyRequestQuota(plan);
        }
        if (watchlistLimit == null) {
            watchlistLimit = ProjectPlan.defaultWatchlistLimit(plan);
        }
    }
}
