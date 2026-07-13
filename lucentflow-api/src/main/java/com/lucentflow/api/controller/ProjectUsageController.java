package com.lucentflow.api.controller;

import com.lucentflow.api.dto.ApiUsageSummaryDTO;
import com.lucentflow.api.security.ProjectContext;
import com.lucentflow.api.service.ProjectApiUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project-scoped API usage reporting.
 *
 * @author ArchLucent
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage API", description = "Per-project API request usage statistics.")
@SecurityRequirement(name = "projectKey")
public class ProjectUsageController {

    private final ProjectApiUsageService projectApiUsageService;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Get current project API usage")
    public ResponseEntity<ApiUsageSummaryDTO> getCurrentUsage(@RequestParam(required = false) Integer days) {
        Long projectId = ProjectContext.getProjectId();
        if (projectId == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(projectApiUsageService.getUsageSummary(projectId, days));
    }
}
