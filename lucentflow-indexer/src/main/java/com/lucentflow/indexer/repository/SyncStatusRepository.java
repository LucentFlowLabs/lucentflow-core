package com.lucentflow.indexer.repository;

import com.lucentflow.common.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA repository for blockchain synchronization status management.
 * 
 * <p>Implementation Details:
 * Extends JpaRepository for standard CRUD operations on SyncStatus entity.
 * Provides optimized queries for synchronization state tracking.
 * Thread-safe through Spring Data repository abstraction.
 * Virtual thread compatible through stateless query operations.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface SyncStatusRepository extends JpaRepository<SyncStatus, Long> {
    
    /**
     * Retrieves the most recent synchronization status record.
     * 
     * <p>Uses descending order by ID to find the latest sync status.
     * Critical for determining blockchain synchronization progress and resumption points.</p>
     * 
     * @return Optional containing the latest SyncStatus if present, empty otherwise
     */
    Optional<SyncStatus> findFirstByOrderByIdDesc();

    /**
     * Updates synchronization progress for a specific row (ID 1 Protocol).
     *
     * <p>Uses a native update to avoid accidental insertion of multiple rows and to ensure
     * deterministic checkpoint persistence on crash/restart boundaries.</p>
     *
     * @param id Row id (must be 1L for ID 1 Protocol)
     * @param blockNumber Latest fully processed block height
     * @param updatedAt Timestamp for audit trail (UTC Instant)
     * @return number of rows updated (0 if row missing)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sync_status " +
            "SET last_scanned_block = :blockNumber, updated_at = :updatedAt " +
            "WHERE id = :id",
            nativeQuery = true)
    int updateProgress(@Param("id") Long id,
                       @Param("blockNumber") Long blockNumber,
                       @Param("updatedAt") Instant updatedAt);
}
