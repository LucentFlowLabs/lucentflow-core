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
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

/**
 * Watchlist entry for targeted monitoring and priority alerting.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "watchlist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address", nullable = false, unique = true, length = 42)
    private String address;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (address != null) {
            address = address.trim().toLowerCase();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
