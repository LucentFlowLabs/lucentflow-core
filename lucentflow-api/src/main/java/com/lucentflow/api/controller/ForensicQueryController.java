package com.lucentflow.api.controller;

import com.lucentflow.api.dto.ForensicEventDTO;
import com.lucentflow.api.service.ForensicQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forensic query endpoints for external integrations.
 *
 * @author ArchLucent
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/forensics")
@Tag(name = "Forensic Query API", description = "Forensic event query endpoints with dynamic filters")
public class ForensicQueryController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ForensicQueryService forensicQueryService;

    @GetMapping("/events")
    @Transactional(readOnly = true)
    @Operation(summary = "Query forensic events", description = "Query high-risk events via dynamic JPA specification filters")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Query executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<Page<ForensicEventDTO>> queryForensicEvents(
            @Parameter(description = "Minimum risk score, inclusive", example = "70")
            @RequestParam(required = false) Integer minRiskScore,
            @Parameter(description = "Maximum risk score, inclusive", example = "100")
            @RequestParam(required = false) Integer maxRiskScore,
            @Parameter(description = "Address match on from/to", example = "0x1234567890abcdef1234567890abcdef12345678")
            @RequestParam(required = false) String address,
            @Parameter(description = "Creation bytecode hash", example = "3db6a4d0a00f5e9fd22e3eaa6f79c1ea861f7cbad9af6ca94dff5f9df627be57")
            @RequestParam(required = false) String bytecodeHash,
            @Parameter(description = "Keyword match inside JSONB risk reasons", example = "REVERT_PROBE")
            @RequestParam(required = false) String reason,
            @Parameter(description = "Page number, default 0", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size, default 20, max 100", example = "20")
            @RequestParam(defaultValue = "20") Integer size
    ) {
        int resolvedPage = page == null || page < 0 ? DEFAULT_PAGE : page;
        int resolvedSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        if (minRiskScore != null && maxRiskScore != null && minRiskScore > maxRiskScore) {
            return ResponseEntity.badRequest().build();
        }

        PageRequest pageable = PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<ForensicEventDTO> result = forensicQueryService.queryEvents(
                minRiskScore, maxRiskScore, address, bytecodeHash, reason, pageable
        );
        return ResponseEntity.ok(result);
    }
}
