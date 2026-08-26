package com.lucentflow.api.integration;

import com.lucentflow.api.service.ForensicQueryService;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.common.repository.AlertRuleRepository;
import com.lucentflow.common.repository.ProjectRepository;
import com.lucentflow.common.repository.SyncStatusRepository;
import com.lucentflow.common.repository.WatchlistRepository;
import com.lucentflow.common.repository.WhaleTransactionRepository;
import com.lucentflow.indexer.config.RpcConcurrencyGovernor;
import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import com.lucentflow.indexer.source.BaseBlockSource;
import com.lucentflow.indexer.source.DirectRpcPermitPort;
import com.lucentflow.pipeline.RpcPermitPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres + Flyway: empty project watchlist yields zero forensic hits
 * even when whale rows exist globally. Also proves API-like flags
 * ({@code enable-indexer=false}) do not advance {@code sync_status} id=1.
 *
 * @author ArchLucent
 * @since 1.2
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
class ForensicTenantIsolationIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lucentflow")
            .withUsername("lucent")
            .withPassword("lucent");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("lucentflow.runtime.enable-indexer", () -> "false");
        registry.add("lucentflow.runtime.enable-analyzer", () -> "false");
        registry.add("lucentflow.basescan.api-key", () -> "test-key");
        registry.add("lucentflow.basescan.base-url", () -> "https://api.basescan.org/api");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ForensicQueryService forensicQueryService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private WhaleTransactionRepository whaleTransactionRepository;

    @Autowired
    private SyncStatusRepository syncStatusRepository;

    private Long projectId;

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @BeforeEach
    void seed() {
        whaleTransactionRepository.deleteAll();
        watchlistRepository.deleteAll();
        alertRuleRepository.deleteAll();
        projectRepository.deleteAll();

        Project project = projectRepository.save(Project.builder()
                .name("empty-watchlist-project")
                .apiKeyHash("a".repeat(64))
                .apiKeyPrefix("emptywl")
                .isActive(true)
                .build());
        projectId = project.getId();

        whaleTransactionRepository.save(WhaleTransaction.builder()
                .hash("0xforensic-isolation-" + Instant.now().toEpochMilli())
                .blockNumber(1L)
                .fromAddress("0xabc0000000000000000000000000000000000001")
                .toAddress("0xdef0000000000000000000000000000000000001")
                .valueEth(new BigDecimal("12.5"))
                .timestamp(Instant.now())
                .isContractCreation(false)
                .transactionType("TRANSFER")
                .riskScore(90)
                .build());
    }

    @Test
    void emptyWatchlist_returnsNoForensicEventsDespiteGlobalWhales() {
        var page = forensicQueryService.queryEvents(
                null, null, null, null, null, projectId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isZero();
        assertThat(whaleTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void indexerDisabled_doesNotCreateScanBeansOrAdvanceId1Checkpoint() {
        assertThat(applicationContext.getBeanProvider(BaseBlockSource.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBeanProvider(RpcConcurrencyGovernor.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBeanProvider(PipelineOrchestrator.class).getIfAvailable()).isNull();
        assertThat(applicationContext.getBean(RpcPermitPort.class)).isInstanceOf(DirectRpcPermitPort.class);

        assertThat(syncStatusRepository.findById(1L)).hasValueSatisfying(status ->
                assertThat(status.getLastScannedBlock())
                        .as("Flyway sentinel must stay 0 when API process starts")
                        .isEqualTo(0L));
    }
}
