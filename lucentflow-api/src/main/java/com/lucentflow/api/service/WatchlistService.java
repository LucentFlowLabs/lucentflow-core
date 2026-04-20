package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.WatchlistCacheService;
import com.lucentflow.api.dto.WatchlistDTO;
import com.lucentflow.api.dto.WatchlistUpsertRequest;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.ProjectRepository;
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
    private final ProjectRepository projectRepository;
    private final WatchlistCacheService watchlistCacheService;

    @Transactional(readOnly = true)
    public List<WatchlistDTO> listAll(Long projectId) {
        return watchlistRepository.findAllByProjectId(projectId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<WatchlistDTO> getById(Long id, Long projectId) {
        return watchlistRepository.findByIdAndProjectId(id, projectId).map(this::toDto);
    }

    @Transactional
    public WatchlistDTO create(WatchlistUpsertRequest request, Long projectId) {
        Project project = requireProject(projectId);
        String normalizedAddress = normalizeAddress(request.address());
        if (watchlistRepository.existsByAddressAndProjectId(normalizedAddress, projectId)) {
            throw new IllegalArgumentException("Address already exists in project watchlist");
        }
        Watchlist item = Watchlist.builder()
                .address(normalizedAddress)
                .label(safeTrim(request.label()))
                .category(safeTrim(request.category()))
                .project(project)
                .build();
        Watchlist saved = watchlistRepository.save(item);
        watchlistCacheService.refresh();
        return toDto(saved);
    }

    @Transactional
    public Optional<WatchlistDTO> update(Long id, WatchlistUpsertRequest request, Long projectId) {
        Optional<Watchlist> existingOpt = watchlistRepository.findByIdAndProjectId(id, projectId);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        Watchlist existing = existingOpt.get();
        String normalizedAddress = normalizeAddress(request.address());
        if (!normalizedAddress.equals(existing.getAddress())
                && watchlistRepository.existsByAddressAndProjectId(normalizedAddress, projectId)) {
            throw new IllegalArgumentException("Address already exists in project watchlist");
        }
        existing.setAddress(normalizedAddress);
        existing.setLabel(safeTrim(request.label()));
        existing.setCategory(safeTrim(request.category()));
        Watchlist saved = watchlistRepository.save(existing);
        watchlistCacheService.refresh();
        return Optional.of(toDto(saved));
    }

    @Transactional
    public boolean delete(Long id, Long projectId) {
        Optional<Watchlist> existing = watchlistRepository.findByIdAndProjectId(id, projectId);
        if (existing.isEmpty()) {
            return false;
        }
        watchlistRepository.delete(existing.get());
        watchlistCacheService.refresh();
        return true;
    }

    private WatchlistDTO toDto(Watchlist item) {
        return new WatchlistDTO(
                item.getId(),
                item.getProject() == null ? null : item.getProject().getId(),
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

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project scope is required");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }
}
