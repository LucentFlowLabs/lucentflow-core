package com.lucentflow.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.WatchlistRepository;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.FluentQuery;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tenant isolation: empty watchlist and null {@code projectId} must not leak the global whale table.
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

    @Test
    void queryEvents_nullProjectId_returnsEmptyPageWithoutRepositoryAccess() {
        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());

        var page = service.queryEvents(null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(watchlistRepository, whaleTransactionRepository);
    }

    @Test
    void exportJson_nullProjectId_writesEmptyArrayWithoutRepositoryAccess() throws Exception {
        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.exportJson(null, null, null, null, null, null, 10_000, out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("[]");
        verifyNoInteractions(watchlistRepository, whaleTransactionRepository);
    }

    @Test
    void watchlistScope_emptyWatchlist_isWatchlistEmpty() {
        when(watchlistRepository.findAllByProjectId(9L)).thenReturn(List.of());
        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());
        assertThat(service.watchlistScope(9L)).isEqualTo("watchlist-empty");
    }

    @Test
    void resolveExportRowLimit_clampsToConfiguredMax() {
        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());

        assertThat(service.resolveExportRowLimit(null)).isEqualTo(10_000);
        assertThat(service.resolveExportRowLimit(0)).isEqualTo(10_000);
        assertThat(service.resolveExportRowLimit(50)).isEqualTo(50);
        assertThat(service.resolveExportRowLimit(999_999)).isEqualTo(10_000);
    }

    @Test
    void countEvents_nullProjectId_skipsRepositories() {
        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());

        assertThat(service.countEvents(null, null, null, null, null, null)).isZero();
        verifyNoInteractions(watchlistRepository, whaleTransactionRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportJson_appliesSqlLimitOnFluentQuery() throws Exception {
        when(watchlistRepository.findAllByProjectId(9L)).thenReturn(List.of());
        FluentQuery.FetchableFluentQuery<WhaleTransaction> fluent =
                mock(FluentQuery.FetchableFluentQuery.class);
        when(fluent.sortBy(any(Sort.class))).thenReturn(fluent);
        when(fluent.limit(500)).thenReturn(fluent);
        when(fluent.stream()).thenReturn(Stream.empty());
        when(whaleTransactionRepository.findBy(any(Specification.class), any())).thenAnswer(invocation -> {
            Function<FluentQuery.FetchableFluentQuery<WhaleTransaction>, Stream<?>> fn =
                    invocation.getArgument(1);
            return fn.apply(fluent);
        });

        ForensicQueryService service = new ForensicQueryService(
                whaleTransactionRepository, watchlistRepository, new ObjectMapper());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.exportJson(null, null, null, null, null, 9L, 500, out);

        verify(fluent).limit(500);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("[]");
    }
}
