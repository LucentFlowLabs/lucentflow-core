package com.lucentflow.api.controller;

import com.lucentflow.api.config.ConditionalOnApiEnabled;

import com.lucentflow.analyzer.service.FundingTopologyService;
import com.lucentflow.api.dto.FundingEdgeDTO;
import com.lucentflow.common.entity.FundingEdge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genesis Trace 3.0 topology query API (Postgres-backed funding graph).
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/forensics/topology")
@Tag(name = "Funding Topology API", description = "Query funding edges for Genesis Trace 3.0.")
@SecurityRequirements
@SecurityRequirement(name = "projectKey")
public class TopologyQueryController {

    private final FundingTopologyService fundingTopologyService;

    @GetMapping("/{address}")
    @Operation(summary = "List inbound/outbound funding edges for an address")
    public ResponseEntity<Map<String, Object>> topology(
            @PathVariable String address,
            @RequestParam(defaultValue = "both") String direction
    ) {
        if (address == null || address.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        String dir = direction == null ? "both" : direction.trim().toLowerCase(Locale.ROOT);
        List<FundingEdgeDTO> inbound = List.of();
        List<FundingEdgeDTO> outbound = List.of();
        if ("inbound".equals(dir) || "both".equals(dir) || "in".equals(dir)) {
            inbound = fundingTopologyService.inbound(normalized).stream().map(this::toDto).toList();
        }
        if ("outbound".equals(dir) || "both".equals(dir) || "out".equals(dir)) {
            outbound = fundingTopologyService.outbound(normalized).stream().map(this::toDto).toList();
        }
        return ResponseEntity.ok(Map.of(
                "address", normalized,
                "inbound", inbound,
                "outbound", outbound,
                "backend", "postgres-funding-edges"
        ));
    }

    private FundingEdgeDTO toDto(FundingEdge edge) {
        return new FundingEdgeDTO(
                edge.getFunderAddress(),
                edge.getFundedAddress(),
                edge.getHopLayer(),
                edge.getRelatedTxHash(),
                edge.getFunderTag(),
                edge.getBlacklisted()
        );
    }
}
