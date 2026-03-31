package com.lucentflow.indexer.repository;

import com.lucentflow.common.entity.EntityTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link EntityTag} (LucentTag Oracle backing store).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface EntityTagRepository extends JpaRepository<EntityTag, String> {
}
