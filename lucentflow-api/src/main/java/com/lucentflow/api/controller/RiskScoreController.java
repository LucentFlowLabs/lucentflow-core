package com.lucentflow.api.controller;

import com.lucentflow.api.config.ConditionalOnApiEnabled;
import com.lucentflow.api.dto.RiskScoreRequest;
import com.lucentflow.api.service.RiskScoreBusyException;
import com.lucentflow.api.service.RiskScoreService;
import com.lucentflow.api.service.RiskScoreTimeoutException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paid point-lookup API for address / transaction risk scoring.
 *
 * @author ArchLucent
 * @since 1.2
 */
@ConditionalOnApiEnabled
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/risk")
@Tag(name = "Risk Score API", description = "On-demand risk score for an address or transaction hash.")
@SecurityRequirement(name = "projectKey")
public class RiskScoreController {

    private final RiskScoreService riskScoreService;

    @PostMapping("/score")
    @Operation(summary = "Score an address or transaction",
            description = "Does not require a watchlist entry. Uses a dedicated RPC permit pool.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Score computed"),
            @ApiResponse(responseCode = "400", description = "Missing address and txHash"),
            @ApiResponse(responseCode = "503", description = "RPC permit pool exhausted"),
            @ApiResponse(responseCode = "504", description = "Lookup timed out")
    })
    public ResponseEntity<?> score(@RequestBody(required = false) RiskScoreRequest request) {
        try {
            return ResponseEntity.ok(riskScoreService.score(request));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RiskScoreTimeoutException e) {
            return error(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
        } catch (RiskScoreBusyException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
