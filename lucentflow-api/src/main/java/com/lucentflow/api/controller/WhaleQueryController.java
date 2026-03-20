package com.lucentflow.api.controller;

import com.lucentflow.common.entity.WhaleTransaction;
import com.lucentflow.indexer.repository.WhaleTransactionRepository;
import com.lucentflow.indexer.repository.SyncStatusRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for querying whale transactions and sync status.
 * Provides endpoints for whale transaction analysis and blockchain synchronization monitoring.
 * 
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Whale Query API", description = "API for querying whale transactions and sync status")
public class WhaleQueryController {
    
    private final WhaleTransactionRepository whaleTransactionRepository;
    private final SyncStatusRepository syncStatusRepository;
    
    public WhaleQueryController(WhaleTransactionRepository whaleTransactionRepository,
                                SyncStatusRepository syncStatusRepository) {
        this.whaleTransactionRepository = whaleTransactionRepository;
        this.syncStatusRepository = syncStatusRepository;
    }
    
    /**
     * Get paginated list of whale transactions with optional minimum ETH filter.
     * 
     * @param minEth Minimum ETH value to filter (optional)
     * @param page Page number (default: 0)
     * @param size Page size (default: 20, max: 100)
     * @return Paginated list of whale transactions
     */
    @GetMapping("/whales")
    @Operation(
        summary = "Get whale transactions",
        description = "Retrieve paginated list of whale transactions with optional minimum ETH value filter"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved whale transactions",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WhaleTransaction.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid parameters provided"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public ResponseEntity<Page<WhaleTransaction>> getWhaleTransactions(
            @Parameter(description = "Minimum ETH value to filter transactions", example = "10.0")
            @RequestParam(required = false) 
            BigDecimal minEth,
            
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") 
            int page,
            
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") 
            int size) {
        
        // Validate page size
        if (size > 100) {
            size = 100;
        }
        
        // Create pageable with sorting by timestamp descending
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        
        Page<WhaleTransaction> result;
        
        if (minEth != null && minEth.compareTo(BigDecimal.ZERO) > 0) {
            // Filter by minimum ETH value
            result = whaleTransactionRepository.findByValueEthGreaterThanEqual(minEth, pageable);
            log.info("Retrieved {} whale transactions with min ETH >= {} (page {}, size {})", 
                    result.getTotalElements(), minEth, page, size);
        } else {
            // Get all whale transactions
            result = whaleTransactionRepository.findAll(pageable);
            log.info("Retrieved {} whale transactions (page {}, size {})", 
                    result.getTotalElements(), page, size);
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get current blockchain synchronization status.
     * 
     * @return Current sync status including last scanned block
     */
    @GetMapping("/sync-status")
    @Operation(
        summary = "Get sync status",
        description = "Retrieve current blockchain synchronization status including last scanned block"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved sync status",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Sync status not found"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Optional<com.lucentflow.common.entity.SyncStatus> syncStatusOpt = 
                syncStatusRepository.findFirstByOrderByIdDesc();
        
        if (syncStatusOpt.isPresent()) {
            com.lucentflow.common.entity.SyncStatus syncStatus = syncStatusOpt.get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("lastScannedBlock", syncStatus.getLastScannedBlock());
            response.put("createdAt", syncStatus.getCreatedAt());
            response.put("updatedAt", syncStatus.getUpdatedAt());
            response.put("syncStatus", "ACTIVE");
            
            log.info("Retrieved sync status: last scanned block {}, updated at {}", 
                    syncStatus.getLastScannedBlock(), syncStatus.getUpdatedAt());
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("lastScannedBlock", 0L);
            response.put("createdAt", null);
            response.put("updatedAt", null);
            response.put("syncStatus", "NOT_STARTED");
            
            log.info("No sync status found, returning default values");
            
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * Get whale transaction statistics.
     * 
     * @return Statistics about whale transactions
     */
    @GetMapping("/whales/stats")
    @Operation(
        summary = "Get whale statistics",
        description = "Retrieve statistics about whale transactions including total count and value"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved whale statistics",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public ResponseEntity<Map<String, Object>> getWhaleStatistics() {
        long totalCount = whaleTransactionRepository.count();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWhaleTransactions", totalCount);
        stats.put("databaseStatus", "CONNECTED");
        stats.put("lastUpdated", System.currentTimeMillis());
        
        // Calculate total value (simplified - in production would use aggregate query)
        Optional<WhaleTransaction> maxTransaction = whaleTransactionRepository.findAll()
                .stream()
                .max((a, b) -> a.getValueEth().compareTo(b.getValueEth()));
        
        maxTransaction.ifPresent(tx -> {
            stats.put("largestWhaleTransaction", Map.of(
                    "hash", tx.getHash(),
                    "valueEth", tx.getValueEth(),
                    "fromAddress", tx.getFromAddress(),
                    "toAddress", tx.getToAddress(),
                    "timestamp", tx.getTimestamp()
            ));
        });
        
        log.info("Retrieved whale statistics: {} total transactions", totalCount);
        
        return ResponseEntity.ok(stats);
    }
}
