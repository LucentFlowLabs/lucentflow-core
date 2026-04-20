package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.WatchlistCacheService;
import com.lucentflow.api.dto.WatchlistDTO;
import com.lucentflow.api.dto.WatchlistUpsertRequest;
import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Watchlist CRUD service with cache synchronization.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistCacheService watchlistCacheService;

    @Transactional(readOnly = true)
    public List<WatchlistDTO> listAll() {
        return watchlistRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<WatchlistDTO> getById(Long id) {
        return watchlistRepository.findById(id).map(this::toDto);
    }

    @Transactional
    public WatchlistDTO create(WatchlistUpsertRequest request) {
        String normalizedAddress = normalizeAddress(request.address());
        Watchlist item = Watchlist.builder()
                .address(normalizedAddress)
                .label(safeTrim(request.label()))
                .category(safeTrim(request.category()))
                .build();
        Watchlist saved = watchlistRepository.save(item);
        watchlistCacheService.refresh();
        return toDto(saved);
    }

    @Transactional
    public Optional<WatchlistDTO> update(Long id, WatchlistUpsertRequest request) {
        Optional<Watchlist> existingOpt = watchlistRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        Watchlist existing = existingOpt.get();
        existing.setAddress(normalizeAddress(request.address()));
        existing.setLabel(safeTrim(request.label()));
        existing.setCategory(safeTrim(request.category()));
        Watchlist saved = watchlistRepository.save(existing);
        watchlistCacheService.refresh();
        return Optional.of(toDto(saved));
    }

    @Transactional
    public boolean delete(Long id) {
        if (!watchlistRepository.existsById(id)) {
            return false;
        }
        watchlistRepository.deleteById(id);
        watchlistCacheService.refresh();
        return true;
    }

    private WatchlistDTO toDto(Watchlist item) {
        return new WatchlistDTO(
                item.getId(),
                item.getAddress(),
                item.getLabel(),
                item.getCategory(),
                item.getCreatedAt()
        );
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address is required");
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Label and category are required");
        }
        return value.trim();
    }
}
