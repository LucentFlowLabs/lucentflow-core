package com.lucentflow.api.controller;

import com.lucentflow.api.config.ConditionalOnApiEnabled;

import com.lucentflow.api.dto.ApiUsageSummaryDTO;
import com.lucentflow.api.dto.ProjectCreateRequest;
import com.lucentflow.api.dto.ProjectDTO;
import com.lucentflow.api.dto.ProjectUpdateRequest;
import com.lucentflow.api.service.ProjectApiUsageService;
import com.lucentflow.api.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Admin API for project lifecycle management.
 *
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/projects")
@Tag(name = "Project Admin API", description = "Admin-only project CRUD and key rotation.")
@SecurityRequirements
@SecurityRequirement(name = "adminKey")
public class ProjectAdminController {

    private final ProjectService projectService;
    private final ProjectApiUsageService projectApiUsageService;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List all projects")
    public ResponseEntity<List<ProjectDTO>> listProjects() {
        return ResponseEntity.ok(projectService.listAll());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get project by ID")
    @ApiResponse(responseCode = "404", description = "Project not found")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable Long id) {
        Optional<ProjectDTO> result = projectService.getById(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create project")
    public ResponseEntity<ProjectDTO> createProject(@RequestBody ProjectCreateRequest request) {
        try {
            return ResponseEntity.ok(projectService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project")
    public ResponseEntity<ProjectDTO> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectUpdateRequest request) {
        try {
            Optional<ProjectDTO> result = projectService.update(id, request);
            return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/rotate-key")
    @Operation(summary = "Rotate project API key")
    public ResponseEntity<ProjectDTO> rotateProjectKey(@PathVariable Long id) {
        Optional<ProjectDTO> result = projectService.rotateApiKey(id);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/usage")
    @Transactional(readOnly = true)
    @Operation(summary = "Get project API usage")
    public ResponseEntity<ApiUsageSummaryDTO> getProjectUsage(
            @PathVariable Long id,
            @RequestParam(required = false) Integer days) {
        Optional<ProjectDTO> project = projectService.getById(id);
        if (project.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(projectApiUsageService.getUsageSummary(id, days));
    }
}
