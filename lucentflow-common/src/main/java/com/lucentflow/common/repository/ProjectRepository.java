package com.lucentflow.common.repository;

import com.lucentflow.common.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for project-level access scopes.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByApiKeyHashAndIsActiveTrue(String apiKeyHash);

    List<Project> findAllByOrderByCreatedAtDesc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByApiKeyHash(String apiKeyHash);
}
