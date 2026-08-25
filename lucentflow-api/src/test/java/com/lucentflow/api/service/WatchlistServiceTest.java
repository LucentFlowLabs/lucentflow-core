package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.WatchlistCacheService;
import com.lucentflow.api.dto.WatchlistUpsertRequest;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.ProjectRepository;
import com.lucentflow.common.repository.WatchlistRepository;
import com.lucentflow.common.usage.WatchlistCapLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Watchlist writes honor the project's commercial address cap via atomic occupancy.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WatchlistCacheService watchlistCacheService;
    @Mock
    private WatchlistCapLedger watchlistCapLedger;

    @InjectMocks
    private WatchlistService watchlistService;

    @Test
    void create_rejectsWhenWatchlistLimitReached() {
        Project project = Project.builder().id(4L).watchlistLimit(50).build();
        when(projectRepository.findById(4L)).thenReturn(Optional.of(project));
        when(watchlistRepository.existsByAddressAndProjectId("0xabc0000000000000000000000000000000000001", 4L))
                .thenReturn(false);
        when(watchlistCapLedger.tryReserve(4L, 50)).thenReturn(false);

        assertThatThrownBy(() -> watchlistService.create(
                new WatchlistUpsertRequest("0xabc0000000000000000000000000000000000001", "Lab", "WATCH"),
                4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist limit exceeded");
        verify(watchlistRepository, never()).save(any(Watchlist.class));
        verify(watchlistCapLedger, never()).release(4L);
    }

    @Test
    void create_savesWhenUnderLimit() {
        Project project = Project.builder().id(4L).watchlistLimit(50).build();
        when(projectRepository.findById(4L)).thenReturn(Optional.of(project));
        when(watchlistRepository.existsByAddressAndProjectId("0xabc0000000000000000000000000000000000001", 4L))
                .thenReturn(false);
        when(watchlistCapLedger.tryReserve(4L, 50)).thenReturn(true);
        when(watchlistRepository.save(any(Watchlist.class))).thenAnswer(invocation -> {
            Watchlist saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return saved;
        });

        var dto = watchlistService.create(
                new WatchlistUpsertRequest("0xABC0000000000000000000000000000000000001", "Lab", "WATCH"),
                4L);

        assertThat(dto.id()).isEqualTo(11L);
        assertThat(dto.address()).isEqualTo("0xabc0000000000000000000000000000000000001");
        verify(watchlistCacheService).refresh();
        verify(watchlistCapLedger, never()).release(4L);
        verify(watchlistRepository, never()).countByProjectId(4L);
    }

    @Test
    void create_unlimitedWhenWatchlistLimitIsZero() {
        Project project = Project.builder().id(4L).watchlistLimit(0).build();
        when(projectRepository.findById(4L)).thenReturn(Optional.of(project));
        when(watchlistRepository.existsByAddressAndProjectId("0xabc0000000000000000000000000000000000001", 4L))
                .thenReturn(false);
        when(watchlistCapLedger.tryReserve(4L, 0)).thenReturn(true);
        when(watchlistRepository.save(any(Watchlist.class))).thenAnswer(invocation -> {
            Watchlist saved = invocation.getArgument(0);
            saved.setId(12L);
            saved.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return saved;
        });

        var dto = watchlistService.create(
                new WatchlistUpsertRequest("0xabc0000000000000000000000000000000000001", "Lab", "WATCH"),
                4L);

        assertThat(dto.id()).isEqualTo(12L);
        verify(watchlistCapLedger).tryReserve(4L, 0);
        verify(watchlistRepository, never()).countByProjectId(4L);
        verify(watchlistCacheService).refresh();
    }

    @Test
    void create_duplicateInsert_refundsReservation() {
        Project project = Project.builder().id(4L).watchlistLimit(50).build();
        when(projectRepository.findById(4L)).thenReturn(Optional.of(project));
        when(watchlistRepository.existsByAddressAndProjectId("0xabc0000000000000000000000000000000000001", 4L))
                .thenReturn(false);
        when(watchlistCapLedger.tryReserve(4L, 50)).thenReturn(true);
        when(watchlistRepository.save(any(Watchlist.class)))
                .thenThrow(new DataIntegrityViolationException("uk_watchlist_project_address"));

        assertThatThrownBy(() -> watchlistService.create(
                new WatchlistUpsertRequest("0xabc0000000000000000000000000000000000001", "Lab", "WATCH"),
                4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Address already exists");
        verify(watchlistCapLedger).release(4L);
        verify(watchlistCacheService, never()).refresh();
    }

    @Test
    void delete_releasesOccupancySlot() {
        Project project = Project.builder().id(4L).watchlistLimit(50).build();
        Watchlist existing = Watchlist.builder()
                .id(9L)
                .address("0xabc0000000000000000000000000000000000001")
                .label("Lab")
                .category("WATCH")
                .project(project)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(watchlistRepository.findByIdAndProjectId(9L, 4L)).thenReturn(Optional.of(existing));

        assertThat(watchlistService.delete(9L, 4L)).isTrue();
        verify(watchlistRepository).delete(existing);
        verify(watchlistCapLedger).release(4L);
        verify(watchlistCacheService).refresh();
    }
}
