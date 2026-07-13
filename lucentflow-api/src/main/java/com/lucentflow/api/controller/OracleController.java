package com.lucentflow.api.controller;

import com.lucentflow.api.config.ConditionalOnApiEnabled;

import com.lucentflow.api.service.EthPriceOracleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Public price oracle surface (Phase 4 multi-asset foundation).
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/oracle")
@Tag(name = "Price Oracle API", description = "Cached ETH/USD valuation helper.")
@SecurityRequirements
public class OracleController {

    private final EthPriceOracleService ethPriceOracleService;

    @GetMapping("/eth-usd")
    @Operation(summary = "Get cached ETH/USD price")
    public ResponseEntity<Map<String, Object>> ethUsd() {
        BigDecimal price = ethPriceOracleService.ethUsd();
        if (price == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Oracle unavailable"));
        }
        return ResponseEntity.ok(Map.of("asset", "ETH", "quote", "USD", "price", price));
    }
}
