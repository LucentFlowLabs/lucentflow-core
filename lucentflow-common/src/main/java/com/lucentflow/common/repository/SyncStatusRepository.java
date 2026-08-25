package com.lucentflow.common.repository;

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
     * Updates synchronization progress for a specific row (ID 1 Protocol).
     *
     * <p>Uses a native update to avoid accidental insertion of multiple rows and to ensure
     * deterministic checkpoint persistence on crash/restart boundaries.</p>
     *
     * <p>Height is monotonic: {@code GREATEST} so out-of-order async chunk checkpoints
     * cannot regress {@code last_scanned_block}. The row is still matched when the
     * incoming height is lower, so a return of {@code 0} means the id=1 row is missing.</p>
     *
     * @param id Row id (must be 1L for ID 1 Protocol)
     * @param blockNumber Latest fully processed block height
     * @param updatedAt Timestamp for audit trail (UTC Instant)
     * @return number of rows updated (0 if row missing)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sync_status " +
            "SET last_scanned_block = GREATEST(last_scanned_block, :blockNumber), updated_at = :updatedAt " +
            "WHERE id = :id",
            nativeQuery = true)
    int updateProgress(@Param("id") Long id,
                       @Param("blockNumber") Long blockNumber,
                       @Param("updatedAt") Instant updatedAt);

    /**
     * Atomic UPSERT for ID=1 protocol checkpoint.
     * <p>
     * Used to avoid optimistic locking/version mismatch when the DB is empty
     * (e.g., after TRUNCATE) and multiple virtual threads start concurrently.
     * On conflict, height is monotonic ({@code GREATEST}) so a stale in-memory
     * alignment write cannot regress {@code last_scanned_block}.
     * </p>
     *
     * @param id  sync_status primary key (must be 1L for ID=1 protocol)
     * @param block last scanned block number
     */
    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO sync_status (id, last_scanned_block, sync_status, created_at, updated_at) " +
                    "VALUES (:id, :block, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (id) DO UPDATE SET last_scanned_block = GREATEST(sync_status.last_scanned_block, EXCLUDED.last_scanned_block), updated_at = CURRENT_TIMESTAMP",
            nativeQuery = true
    )
    void upsertProgress(@Param("id") Long id, @Param("block") Long block);

    /**
     * Observability heartbeat: chain tip, lag, and approximate throughput (ID=1 protocol).
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sync_status SET chain_head_block = :head, block_lag = :lag, "
            + "blocks_per_second = :bps, updated_at = :updatedAt WHERE id = :id",
            nativeQuery = true)
    int updateSyncMetrics(@Param("id") Long id,
                          @Param("head") Long head,
                          @Param("lag") Long lag,
                          @Param("bps") Double bps,
                          @Param("updatedAt") Instant updatedAt);
}
