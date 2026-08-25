package com.lucentflow.api.controller;

import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.api.dto.FundingEdgeDTO;
import com.lucentflow.api.security.ProjectContext;
import com.lucentflow.common.entity.FundingEdge;
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
 * Topology queries gate the lookup address on the project watchlist; hits return global edges.
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

    @Test
    void topology_watchlistHit_returnsGlobalEdgesForCounterpartyOutsideWatchlist() {
        ProjectContext.set(Project.builder().id(8L).build());
        String address = "0x6ac359924348dd492a7751af122d781db984b70a";
        String counterparty = "0x1111111111111111111111111111111111111111";
        when(watchlistRepository.existsByAddressAndProjectId(address, 8L)).thenReturn(true);
        when(fundingTopologyService.inbound(address)).thenReturn(List.of(
                FundingEdge.builder()
                        .funderAddress(counterparty)
                        .fundedAddress(address)
                        .hopLayer(2)
                        .relatedTxHash("0xabc")
                        .funderTag("MIXER")
                        .blacklisted(true)
                        .build()));
        when(fundingTopologyService.outbound(address)).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.topology(address, "both");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("scope")).isEqualTo("watchlist");
        @SuppressWarnings("unchecked")
        List<FundingEdgeDTO> inbound = (List<FundingEdgeDTO>) response.getBody().get("inbound");
        assertThat(inbound).hasSize(1);
        assertThat(inbound.getFirst().funderAddress()).isEqualTo(counterparty);
        assertThat(inbound.getFirst().blacklisted()).isTrue();
        assertThat(response.getBody().get("outbound")).isEqualTo(List.of());
    }

    @Test
    void topology_missingProjectContext_returns401() {
        ResponseEntity<Map<String, Object>> response = controller.topology(
                "0x6ac359924348dd492a7751af122d781db984b70a", "both");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(watchlistRepository, fundingTopologyService);
    }
}
