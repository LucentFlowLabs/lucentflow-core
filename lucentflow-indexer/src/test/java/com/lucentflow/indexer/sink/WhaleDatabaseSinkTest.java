package com.lucentflow.indexer.sink;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Persistence failures must propagate so callers can skip alerts.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class WhaleDatabaseSinkTest {

    @Mock
    private WhaleTransactionRepository whaleTransactionRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private WhaleDatabaseSink sink;

    @Test
    void saveWhaleTransactions_rethrowsWhenBatchUpdateFails() {
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> sink.saveWhaleTransactions(List.of(sampleTx())))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("db down");
    }

    @Test
    void saveWhaleTransactions_emptyList_doesNotTouchJdbc() {
        sink.saveWhaleTransactions(List.of());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    private static WhaleTransaction sampleTx() {
        return WhaleTransaction.builder()
                .hash("0xabc")
                .fromAddress("0xfrom")
                .valueEth(BigDecimal.ONE)
                .blockNumber(1L)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .isContractCreation(false)
                .build();
    }
}
