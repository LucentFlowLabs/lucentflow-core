package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.EntityTag;
import com.lucentflow.common.entity.EntityTagCategory;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.EntityTagRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LucentTag Oracle: in-memory map of all {@link EntityTag} rows for O(1) resolution during indexing,
 * with thread-safe DB + cache updates.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagOracleService {

    private final EntityTagRepository entityTagRepository;

    private final ConcurrentHashMap<String, String> tagByAddressLower = new ConcurrentHashMap<>();

    @PostConstruct
    void loadCacheFromDatabase() {
        reloadFromDatabase();
    }

    /**
     * Replaces the in-memory map with the current database snapshot (e.g. after bulk admin updates).
     */
    public synchronized void reloadFromDatabase() {
        tagByAddressLower.clear();
        for (EntityTag row : entityTagRepository.findAll()) {
            String k = normalizeAddress(row.getAddress());
            if (k != null) {
                tagByAddressLower.put(k, row.getTagName());
            }
        }
        log.info("[LUCENT-TAG] Loaded {} oracle labels into cache.", tagByAddressLower.size());
    }

    /**
     * @return display tag name, or {@code null} if unknown
     */
    public String resolveTag(String address) {
        String k = normalizeAddress(address);
        if (k == null) {
            return null;
        }
        return tagByAddressLower.get(k);
    }

    /**
     * Upserts a tag in PostgreSQL and updates the hot cache (same visibility as DB commit).
     */
    @Transactional
    public synchronized void updateTag(String address, String name, String category) {
        String normalized = normalizeAddress(address);
        if (normalized == null || name == null || name.isBlank()) {
            return;
        }
        EntityTagCategory cat;
        try {
            cat = EntityTagCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("[LUCENT-TAG] Ignoring update: invalid category '{}'", category);
            return;
        }
        EntityTag entity = entityTagRepository.findById(normalized)
                .orElse(EntityTag.builder()
                        .address(normalized)
                        .metadata("{}")
                        .riskScoreModifier(0)
                        .build());
        entity.setTagName(name.trim());
        entity.setCategory(cat);
        if (entity.getMetadata() == null || entity.getMetadata().isBlank()) {
            entity.setMetadata("{}");
        }
        if (entity.getRiskScoreModifier() == null) {
            entity.setRiskScoreModifier(0);
        }
        entityTagRepository.save(entity);
        tagByAddressLower.put(normalized, entity.getTagName());
        log.debug("[LUCENT-TAG] Upserted {} -> {}", normalized, entity.getTagName());
    }

    /**
     * Overlays oracle labels on {@link WhaleTransaction} fields used by Metabase (non-null wins).
     */
    public void applyResolvedTags(WhaleTransaction whaleTx) {
        if (whaleTx == null) {
            return;
        }
        String fromTag = resolveTag(whaleTx.getFromAddress());
        if (fromTag != null) {
            whaleTx.setFromAddressTag(fromTag);
        }
        String toTag = resolveTag(whaleTx.getToAddress());
        if (toTag != null) {
            whaleTx.setToAddressTag(toTag);
        }
        if (fromTag != null) {
            whaleTx.setAddressTag(fromTag);
        } else if (toTag != null) {
            whaleTx.setAddressTag(toTag);
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String t = address.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.toLowerCase(Locale.ROOT);
    }
}
