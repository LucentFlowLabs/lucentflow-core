package com.lucentflow.api.controller;

import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.api.security.ProjectContext;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.WatchlistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Topology queries stay inside the project's watchlist.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ExtendWith(MockitoExtension.class)
class TopologyQueryControllerTest {

    @Mock
    private FundingTopologyService fundingTopologyService;
    @Mock
    private WatchlistRepository watchlistRepository;

    @InjectMocks
    private TopologyQueryController controller;

    @AfterEach
    void clearContext() {
        ProjectContext.clear();
    }

    @Test
    void topology_addressNotOnWatchlist_returnsEmptyMissScope() {
        ProjectContext.set(Project.builder().id(8L).build());
        String address = "0x6ac359924348dd492a7751af122d781db984b70a";
        when(watchlistRepository.existsByAddressAndProjectId(address, 8L)).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.topology(address, "both");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("scope")).isEqualTo("watchlist-miss");
        assertThat(response.getBody().get("inbound")).isEqualTo(List.of());
        assertThat(response.getBody().get("outbound")).isEqualTo(List.of());
        verifyNoInteractions(fundingTopologyService);
    }
}
