package com.lucentflow.api.controller;

import com.lucentflow.api.dto.WatchlistDTO;
import com.lucentflow.api.dto.WatchlistUpsertRequest;
import com.lucentflow.api.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Watchlist CRUD API for targeted monitoring.
 *
 * @author ArchLucent
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/watchlist")
@Tag(name = "Watchlist API", description = "CRUD operations for watchlist addresses")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List watchlist entries")
    public ResponseEntity<List<WatchlistDTO>> list() {
        return ResponseEntity.ok(watchlistService.listAll());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get watchlist entry by ID")
    @ApiResponse(responseCode = "404", description = "Watchlist entry not found")
    public ResponseEntity<WatchlistDTO> getById(@PathVariable Long id) {
        Optional<WatchlistDTO> result = watchlistService.getById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create watchlist entry")
    public ResponseEntity<WatchlistDTO> create(@RequestBody WatchlistUpsertRequest request) {
        try {
            return ResponseEntity.ok(watchlistService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update watchlist entry")
    public ResponseEntity<WatchlistDTO> update(@PathVariable Long id, @RequestBody WatchlistUpsertRequest request) {
        try {
            Optional<WatchlistDTO> result = watchlistService.update(id, request);
            return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete watchlist entry")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = watchlistService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
