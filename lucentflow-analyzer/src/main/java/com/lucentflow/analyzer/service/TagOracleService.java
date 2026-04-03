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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LucentTag Oracle: immutable snapshot of {@link EntityTag} rows for O(1) resolution during indexing.
 * Copy-on-write updates avoid holding locks across database I/O (virtual-thread pinning safe).
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagOracleService {

    private final EntityTagRepository entityTagRepository;

    /** Guards only snapshot pointer swaps; never held during repository I/O. */
    private final Object snapshotLock = new Object();

    private volatile Map<String, String> tagSnapshot = Map.of();

    @PostConstruct
    void loadCacheFromDatabase() {
        reloadFromDatabase();
    }

    /**
     * Replaces the in-memory snapshot with the current database view (e.g. after bulk admin updates).
     */
    public void reloadFromDatabase() {
        java.util.List<EntityTag> rows = entityTagRepository.findAll();
        Map<String, String> next = new HashMap<>();
        for (EntityTag row : rows) {
            String k = normalizeAddress(row.getAddress());
            if (k != null) {
                next.put(k, row.getTagName());
            }
        }
        Map<String, String> frozen = Map.copyOf(next);
        synchronized (snapshotLock) {
            tagSnapshot = frozen;
        }
        log.info("[LUCENT-TAG] Loaded {} oracle labels into cache.", frozen.size());
    }

    /**
     * @return display tag name, or {@code null} if unknown
     */
    public String resolveTag(String address) {
        String k = normalizeAddress(address);
        if (k == null) {
            return null;
        }
        return tagSnapshot.get(k);
    }

    /**
     * Upserts a tag in PostgreSQL and updates the hot cache (same visibility as DB commit).
     */
    @Transactional
    public void updateTag(String address, String name, String category) {
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
        String tagName = entity.getTagName();
        synchronized (snapshotLock) {
            Map<String, String> neu = new HashMap<>(tagSnapshot);
            neu.put(normalized, tagName);
            tagSnapshot = Map.copyOf(neu);
        }
        log.debug("[LUCENT-TAG] Upserted {} -> {}", normalized, tagName);
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
