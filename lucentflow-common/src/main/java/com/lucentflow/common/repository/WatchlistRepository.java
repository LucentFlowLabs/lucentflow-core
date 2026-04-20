package com.lucentflow.common.repository;

import com.lucentflow.common.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for watchlist entries.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    Optional<Watchlist> findByAddress(String address);

    boolean existsByAddress(String address);
}
