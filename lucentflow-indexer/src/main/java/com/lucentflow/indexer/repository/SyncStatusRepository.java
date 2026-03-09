package com.lucentflow.indexer.repository;

import com.lucentflow.common.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
