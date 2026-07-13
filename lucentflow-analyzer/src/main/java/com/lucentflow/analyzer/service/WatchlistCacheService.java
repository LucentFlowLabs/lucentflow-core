package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.WatchlistRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
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
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, WatchlistMeta>> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Transactional(readOnly = true)
    public synchronized void refresh() {
        Map<String, ConcurrentHashMap<Long, WatchlistMeta>> latest = new ConcurrentHashMap<>();
        for (Watchlist item : watchlistRepository.findAll()) {
            if (item.getProject() == null || item.getProject().getId() == null) {
                continue;
            }
            // Inactive projects must not trigger watchlist-priority alerts.
            if (!Boolean.TRUE.equals(item.getProject().getIsActive())) {
                continue;
            }
            String normalized = normalize(item.getAddress());
            Long projectId = item.getProject().getId();
            if (normalized != null) {
                latest.computeIfAbsent(normalized, key -> new ConcurrentHashMap<>())
                        .put(projectId, new WatchlistMeta(
                                item.getLabel(),
                                item.getCategory(),
                                item.getProject().getWebhookUrl(),
                                item.getProject().getWebhookSecret()
                        ));
            }
        }
        cache.clear();
        cache.putAll(latest);
        log.info("[WATCHLIST] Cache refreshed: {} active-project address keys loaded", cache.size());
    }

    public boolean isWatched(String address) {
        String normalized = normalize(address);
        return normalized != null && cache.containsKey(normalized) && !cache.get(normalized).isEmpty();
    }

    public Optional<WatchlistHit> firstHit(String fromAddress, String toAddress) {
        List<WatchlistHit> hits = findHits(fromAddress, toAddress);
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(hits.get(0));
    }

    public List<WatchlistHit> findHits(String fromAddress, String toAddress) {
        List<WatchlistHit> results = new ArrayList<>();
        String from = normalize(fromAddress);
        if (from != null) {
            results.addAll(projectHits(from));
        }
        String to = normalize(toAddress);
        if (to != null) {
            results.addAll(projectHits(to));
        }
        return results;
    }

    private List<WatchlistHit> projectHits(String address) {
        ConcurrentHashMap<Long, WatchlistMeta> projectMap = cache.get(address);
        if (projectMap == null || projectMap.isEmpty()) {
            return List.of();
        }
        List<WatchlistHit> hits = new ArrayList<>();
        for (Map.Entry<Long, WatchlistMeta> entry : projectMap.entrySet()) {
            WatchlistMeta meta = entry.getValue();
            hits.add(new WatchlistHit(
                    address,
                    meta.label(),
                    meta.category(),
                    entry.getKey(),
                    meta.projectWebhookUrl(),
                    meta.projectWebhookSecret()
            ));
        }
        return hits;
    }

    private String normalize(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private record WatchlistMeta(
            String label,
            String category,
            String projectWebhookUrl,
            String projectWebhookSecret
    ) {
    }

    public record WatchlistHit(
            String address,
            String label,
            String category,
            Long projectId,
            String projectWebhookUrl,
            String projectWebhookSecret
    ) {
    }
}
