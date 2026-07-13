package com.lucentflow.api.controller;

import com.lucentflow.api.dto.BackfillRequest;
import com.lucentflow.indexer.pipeline.PipelineOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Admin historical backfill controls (Phase 4).
 *
 * @author ArchLucent
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/backfill")
@Tag(name = "Backfill Admin API", description = "On-demand historical block range ingestion.")
@SecurityRequirements
@SecurityRequirement(name = "adminKey")
public class BackfillAdminController {

    private final ObjectProvider<PipelineOrchestrator> pipelineOrchestrator;

    public BackfillAdminController(ObjectProvider<PipelineOrchestrator> pipelineOrchestrator) {
        this.pipelineOrchestrator = pipelineOrchestrator;
    }

    @PostMapping
    @Operation(summary = "Backfill a historical block range (does not move sync checkpoint)")
    public ResponseEntity<?> backfill(@RequestBody BackfillRequest request) {
        PipelineOrchestrator orchestrator = pipelineOrchestrator.getIfAvailable();
        if (orchestrator == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Indexer runtime disabled (lucentflow.runtime.enable-indexer=false)"));
        }
        if (request == null || request.fromBlock() == null || request.toBlock() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "fromBlock and toBlock are required"));
        }
        try {
            CompletableFuture<PipelineOrchestrator.BackfillReport> future = CompletableFuture.supplyAsync(
                    () -> orchestrator.backfillHistoricalRange(request.fromBlock(), request.toBlock()));
            PipelineOrchestrator.BackfillReport report = future.join();
            return ResponseEntity.accepted().body(report);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
