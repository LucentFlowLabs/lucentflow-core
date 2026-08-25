package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.WatchlistCacheService;
import com.lucentflow.api.dto.WatchlistUpsertRequest;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.ProjectRepository;
import com.lucentflow.common.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Watchlist writes honor the project's commercial address cap.
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

    @InjectMocks
    private WatchlistService watchlistService;

    @Test
    void create_rejectsWhenWatchlistLimitReached() {
        Project project = Project.builder().id(4L).watchlistLimit(50).build();
        when(projectRepository.findById(4L)).thenReturn(Optional.of(project));
        when(watchlistRepository.existsByAddressAndProjectId("0xabc0000000000000000000000000000000000001", 4L))
                .thenReturn(false);
        when(watchlistRepository.countByProjectId(4L)).thenReturn(50L);

        assertThatThrownBy(() -> watchlistService.create(
                new WatchlistUpsertRequest("0xabc0000000000000000000000000000000000001", "Lab", "WATCH"),
                4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist limit exceeded");
        verify(watchlistRepository, never()).save(any(Watchlist.class));
    }
}
