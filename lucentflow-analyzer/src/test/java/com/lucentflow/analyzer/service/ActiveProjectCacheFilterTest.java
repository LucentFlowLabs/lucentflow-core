package com.lucentflow.analyzer.service;

import com.lucentflow.common.entity.AlertRule;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.entity.Watchlist;
import com.lucentflow.common.repository.AlertRuleRepository;
import com.lucentflow.common.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Ensures inactive projects are excluded from alert/watchlist pipeline caches.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class ActiveProjectCacheFilterTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @InjectMocks
    private AlertRuleCacheService alertRuleCacheService;

    @InjectMocks
    private WatchlistCacheService watchlistCacheService;

    @Test
    void alertRuleCache_skipsInactiveProjects() {
        ReflectionTestUtils.setField(alertRuleCacheService, "globalRiskThreshold", 70);
        when(alertRuleRepository.findAll()).thenReturn(List.of(
                ruleFor(activeProject(1L, "https://a.example/hook")),
                ruleFor(inactiveProject(2L, "https://b.example/hook"))
        ));

        alertRuleCacheService.refresh();

        assertThat(alertRuleCacheService.allRules()).hasSize(1);
        assertThat(alertRuleCacheService.allRules().iterator().next().projectId()).isEqualTo(1L);
        assertThat(alertRuleCacheService.ruleForProject(2L).projectWebhookUrl()).isNull();
    }

    @Test
    void watchlistCache_skipsInactiveProjects() {
        when(watchlistRepository.findAll()).thenReturn(List.of(
                watchlist("0xaaa", activeProject(1L, null)),
                watchlist("0xbbb", inactiveProject(2L, null))
        ));

        watchlistCacheService.refresh();

        assertThat(watchlistCacheService.isWatched("0xaaa")).isTrue();
        assertThat(watchlistCacheService.isWatched("0xbbb")).isFalse();
        assertThat(watchlistCacheService.findHits("0xaaa", "0xbbb")).hasSize(1);
    }

    private static Project activeProject(Long id, String webhook) {
        return Project.builder().id(id).name("active-" + id)
                .apiKeyHash("hash-active-" + id).apiKeyPrefix("active")
                .webhookUrl(webhook).isActive(true).build();
    }

    private static Project inactiveProject(Long id, String webhook) {
        return Project.builder().id(id).name("inactive-" + id)
                .apiKeyHash("hash-inactive-" + id).apiKeyPrefix("inactiv")
                .webhookUrl(webhook).isActive(false).build();
    }

    private static AlertRule ruleFor(Project project) {
        return AlertRule.builder()
                .id(project.getId())
                .project(project)
                .minRiskScore(70)
                .watchlistOnly(false)
                .contractCreationOnly(false)
                .enabled(true)
                .build();
    }

    private static Watchlist watchlist(String address, Project project) {
        return Watchlist.builder()
                .address(address)
                .label("L")
                .category("C")
                .project(project)
                .build();
    }
}
