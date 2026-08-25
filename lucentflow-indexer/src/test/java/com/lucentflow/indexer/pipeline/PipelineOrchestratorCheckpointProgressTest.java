package com.lucentflow.indexer.pipeline;

import com.lucentflow.common.repository.SyncStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Async chunk checkpoints must not regress {@code sync_status.last_scanned_block}.
 *
 * @author ArchLucent
 * @since 1.0
 */
class PipelineOrchestratorCheckpointProgressTest {

    @Test
    void updateProgress_doesNotRegressWhenOlderChunkCompletesLater() throws Exception {
        String sql = nativeSql("updateProgress", Long.class, Long.class, Instant.class);
        try (Connection connection = openDb("c2_update_progress")) {
            NamedParameterJdbcTemplate jdbc = namedJdbc(connection);
            createSingleton(jdbc);
            seedHeight(jdbc, 100L);

            assertThat(applyUpdateProgress(jdbc, sql, 299L)).isEqualTo(1);
            assertThat(readHeight(jdbc)).isEqualTo(299L);

            assertThat(applyUpdateProgress(jdbc, sql, 199L)).isEqualTo(1);
            assertThat(readHeight(jdbc)).isEqualTo(299L);
        }
    }

    @Test
    void upsertProgress_conflictClauseIsMonotonic() throws Exception {
        // H2 MODE=PostgreSQL still rejects ON CONFLICT; lock the production clause instead.
        String sql = nativeSql("upsertProgress", Long.class, Long.class);
        assertThat(sql).contains(
                "ON CONFLICT (id) DO UPDATE SET last_scanned_block = GREATEST(sync_status.last_scanned_block, EXCLUDED.last_scanned_block)");
    }

    private static String nativeSql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SyncStatusRepository.class.getMethod(methodName, parameterTypes);
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        return query.value();
    }

    private static Connection openDb(String name) throws Exception {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    private static NamedParameterJdbcTemplate namedJdbc(Connection connection) {
        return new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private static void createSingleton(NamedParameterJdbcTemplate jdbc) {
        jdbc.getJdbcOperations().execute("""
                CREATE TABLE sync_status (
                    id BIGINT PRIMARY KEY,
                    last_scanned_block BIGINT NOT NULL,
                    sync_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static void seedHeight(NamedParameterJdbcTemplate jdbc, long height) {
        jdbc.update(
                "INSERT INTO sync_status (id, last_scanned_block, sync_status) VALUES (1, :height, 'ACTIVE')",
                new MapSqlParameterSource("height", height));
    }

    private static int applyUpdateProgress(NamedParameterJdbcTemplate jdbc, String sql, long blockNumber) {
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", 1L)
                .addValue("blockNumber", blockNumber)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    private static long readHeight(NamedParameterJdbcTemplate jdbc) {
        Long height = jdbc.getJdbcOperations().queryForObject(
                "SELECT last_scanned_block FROM sync_status WHERE id = 1", Long.class);
        assertThat(height).isNotNull();
        return height;
    }
}
