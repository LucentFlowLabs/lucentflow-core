package com.lucentflow.api.controller;

import com.lucentflow.api.dto.AlertRuleDTO;
import com.lucentflow.api.dto.AlertRuleUpsertRequest;
import com.lucentflow.api.security.ProjectContext;
import com.lucentflow.api.service.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-project alert rule configuration API.
 *
 * @author ArchLucent
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alert-rules")
@Tag(name = "Alert Rules API", description = "Project-scoped alert threshold and routing rules.")
@SecurityRequirement(name = "projectKey")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Get current project alert rule")
    public ResponseEntity<AlertRuleDTO> getCurrentRule() {
        Long projectId = ProjectContext.getProjectId();
        if (projectId == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(alertRuleService.getForProject(projectId));
    }

    @PutMapping
    @Operation(summary = "Create or update current project alert rule")
    public ResponseEntity<AlertRuleDTO> upsert(@RequestBody AlertRuleUpsertRequest request) {
        Long projectId = ProjectContext.getProjectId();
        if (projectId == null) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(alertRuleService.upsert(request, projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
