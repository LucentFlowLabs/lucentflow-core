package com.lucentflow.common.repository;

import com.lucentflow.common.entity.ProjectApiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for per-project daily API usage counters.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface ProjectApiUsageRepository extends JpaRepository<ProjectApiUsage, Long> {

    List<ProjectApiUsage> findByProjectIdAndUsageDateGreaterThanEqualOrderByUsageDateAsc(
            Long projectId,
            LocalDate fromDate
    );

    @Query("""
            SELECT COALESCE(SUM(u.requestCount), 0)
            FROM ProjectApiUsage u
            WHERE u.project.id = :projectId
            """)
    long sumRequestCountByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Query(value = """
            INSERT INTO project_api_usage (project_id, usage_date, request_count, updated_at)
            VALUES (:projectId, CURRENT_DATE, 1, CURRENT_TIMESTAMP)
            ON CONFLICT (project_id, usage_date)
            DO UPDATE SET
                request_count = project_api_usage.request_count + 1,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void incrementDailyCount(@Param("projectId") Long projectId);
}
