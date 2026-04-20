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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final DateTimeFormatter EXPORT_FILENAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

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

    @GetMapping(value = "/events/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    @Operation(summary = "Export forensic events as JSON", description = "Stream filtered forensic events as JSON array")
    public ResponseEntity<StreamingResponseBody> exportForensicEventsAsJson(
            @RequestParam(required = false) Integer minRiskScore,
            @RequestParam(required = false) Integer maxRiskScore,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String bytecodeHash,
            @RequestParam(required = false) String reason
    ) {
        if (minRiskScore != null && maxRiskScore != null && minRiskScore > maxRiskScore) {
            return ResponseEntity.badRequest().build();
        }
        String filename = "lucentflow-forensics-" + EXPORT_FILENAME_TIME.format(Instant.now()) + ".json";
        StreamingResponseBody body = outputStream -> executeOnVirtualThread(() ->
                forensicQueryService.exportJson(minRiskScore, maxRiskScore, address, bytecodeHash, reason, outputStream));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping(value = "/events/export/csv", produces = "text/csv")
    @Transactional(readOnly = true)
    @Operation(summary = "Export forensic events as CSV", description = "Stream filtered forensic events as CSV with UTF-8 BOM")
    public ResponseEntity<StreamingResponseBody> exportForensicEventsAsCsv(
            @RequestParam(required = false) Integer minRiskScore,
            @RequestParam(required = false) Integer maxRiskScore,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String bytecodeHash,
            @RequestParam(required = false) String reason
    ) {
        if (minRiskScore != null && maxRiskScore != null && minRiskScore > maxRiskScore) {
            return ResponseEntity.badRequest().build();
        }
        String filename = "lucentflow-forensics-" + EXPORT_FILENAME_TIME.format(Instant.now()) + ".csv";
        StreamingResponseBody body = outputStream -> executeOnVirtualThread(() ->
                forensicQueryService.exportCsv(minRiskScore, maxRiskScore, address, bytecodeHash, reason, outputStream));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    private void executeOnVirtualThread(IoTask task) throws IOException {
        if (Thread.currentThread().isVirtual()) {
            task.run();
            return;
        }
        AtomicReference<IOException> errorRef = new AtomicReference<>();
        Thread thread = Thread.ofVirtual().name("forensics-export-vt").unstarted(() -> {
            try {
                task.run();
            } catch (IOException e) {
                errorRef.set(e);
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Export interrupted while waiting for virtual thread", e);
        }
        IOException exportError = errorRef.get();
        if (exportError != null) {
            throw exportError;
        }
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }
}
