package com.lucentflow.common.repository;

import com.lucentflow.common.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for project-level access scopes.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByApiKeyAndIsActiveTrue(String apiKey);
}
