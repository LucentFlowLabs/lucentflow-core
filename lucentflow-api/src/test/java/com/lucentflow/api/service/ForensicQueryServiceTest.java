package com.lucentflow.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.common.repository.WatchlistRepository;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant isolation: empty project watchlist must yield an empty forensic page.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class ForensicQueryServiceTest {

    @Mock
    private WhaleTransactionRepository whaleTransactionRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @Test
    void queryEvents_emptyWatchlist_returnsEmptyPage() {
        when(watchlistRepository.findAllByProjectId(9L)).thenReturn(List.of());
        when(whaleTransactionRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of()));

        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());

        var page = service.queryEvents(null, null, null, null, null, 9L, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(watchlistRepository).findAllByProjectId(9L);
        verify(whaleTransactionRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }
}
