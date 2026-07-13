package com.lucentflow.common.repository;

import com.lucentflow.common.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for per-project alert rule configuration.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByProjectId(Long projectId);
}
