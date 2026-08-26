package com.lucentflow.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for the ID=1 indexer checkpoint ({@code sync_status}).
 *
 * <p>{@code lastScannedBlock} is enqueue high-water, not UPSERT completion.
 * Production writes use {@link com.lucentflow.common.repository.SyncStatusRepository}
 * native SQL, not JPA {@code save}.</p>
 *
 * @author ArchLucent
 * @since 1.0
 */
@Entity
@Table(name = "sync_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncStatus {

    @Id
    private Long id;

    /** Enqueue high-water; crash before UPSERT is at-most-once for those hashes. */
    @Column(name = "last_scanned_block", nullable = false)
    private Long lastScannedBlock;

    @Column(name = "sync_status", length = 32)
    private String syncStatus;

    @Column(name = "chain_head_block")
    private Long chainHeadBlock;

    @Column(name = "block_lag")
    private Long blockLag;

    @Column(name = "blocks_per_second")
    private Double blocksPerSecond;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JPA lifecycle callback executed before entity persistence.
     * 
     * <p>Initializes creation and update timestamps to ensure data integrity.
     * Guarantees consistent timestamp values for new sync status records.</p>
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = 1L;
        }
        if (syncStatus == null) {
            syncStatus = "ACTIVE";
        }
        createdAt = now;
        updatedAt = now;
    }

    /**
     * JPA lifecycle callback executed before entity updates.
     * 
     * <p>Updates the modification timestamp to maintain accurate audit trail.
     * Ensures change tracking across synchronization status updates.</p>
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Compares this SyncStatus with another object for equality.
     * 
     * <p>Uses primary key comparison for entity equality as per JPA best practices.
     * Returns false for null or different class types.</p>
     * 
     * @param o Object to compare with
     * @return true if objects represent the same SyncStatus entity, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SyncStatus)) return false;
        return id != null && id.equals(((SyncStatus) o).id);
    }

    /**
     * Generates hash code for the entity using class-based hashing.
     * 
     * <p>Uses class hash code instead of field-based hashing to maintain
     * consistency with JPA entity identity management and proxy handling.</p>
     * 
     * @return Hash code value for this entity
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Returns string representation of the SyncStatus entity.
     * 
     * <p>Includes key fields for debugging and logging purposes.
     * Format: SyncStatus{id=X, lastScannedBlock=Y, createdAt=Z, updatedAt=W}</p>
     * 
     * @return String representation containing entity state
     */
    @Override
    public String toString() {
        return "SyncStatus{" +
                "id=" + id +
                ", lastScannedBlock=" + lastScannedBlock +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
