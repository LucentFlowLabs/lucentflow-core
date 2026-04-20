package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.WatchlistRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory watchlist cache with O(1) address lookup.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistCacheService {

    private final WatchlistRepository watchlistRepository;
    private final ConcurrentHashMap<String, WatchlistMeta> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Transactional(readOnly = true)
    public synchronized void refresh() {
        Map<String, WatchlistMeta> latest = new ConcurrentHashMap<>();
        for (Watchlist item : watchlistRepository.findAll()) {
            String normalized = normalize(item.getAddress());
            if (normalized != null) {
                latest.put(normalized, new WatchlistMeta(item.getLabel(), item.getCategory()));
            }
        }
        cache.clear();
        cache.putAll(latest);
        log.info("[WATCHLIST] Cache refreshed: {} entries loaded", cache.size());
    }

    public boolean isWatched(String address) {
        String normalized = normalize(address);
        return normalized != null && cache.containsKey(normalized);
    }

    public Optional<WatchlistHit> firstHit(String fromAddress, String toAddress) {
        String from = normalize(fromAddress);
        if (from != null) {
            WatchlistMeta fromMeta = cache.get(from);
            if (fromMeta != null) {
                return Optional.of(new WatchlistHit(from, fromMeta.label(), fromMeta.category()));
            }
        }
        String to = normalize(toAddress);
        if (to != null) {
            WatchlistMeta toMeta = cache.get(to);
            if (toMeta != null) {
                return Optional.of(new WatchlistHit(to, toMeta.label(), toMeta.category()));
            }
        }
        return Optional.empty();
    }

    private String normalize(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private record WatchlistMeta(String label, String category) {
    }

    public record WatchlistHit(String address, String label, String category) {
    }
}
