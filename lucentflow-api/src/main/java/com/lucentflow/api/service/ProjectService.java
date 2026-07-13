package com.lucentflow.api.service;

import com.lucentflow.analyzer.service.AlertRuleCacheService;
import com.lucentflow.analyzer.service.WatchlistCacheService;
import com.lucentflow.api.dto.ProjectCreateRequest;
import com.lucentflow.api.dto.ProjectDTO;
import com.lucentflow.api.dto.ProjectUpdateRequest;
import com.lucentflow.api.util.ProjectApiKeyGenerator;
import com.lucentflow.common.entity.AlertRule;
import com.lucentflow.common.entity.Project;
import com.lucentflow.common.repository.AlertRuleRepository;
import com.lucentflow.common.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Admin project lifecycle management.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertRuleCacheService alertRuleCacheService;
    private final WatchlistCacheService watchlistCacheService;

    @Value("${lucentflow.alert.global-risk-threshold:70}")
    private int globalRiskThreshold;

    @Transactional(readOnly = true)
    public List<ProjectDTO> listAll() {
        return projectRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(project -> toDto(project, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProjectDTO> getById(Long id) {
        return projectRepository.findById(id).map(project -> toDto(project, null));
    }

    @Transactional
    public ProjectDTO create(ProjectCreateRequest request) {
        validateCreateRequest(request);
        String name = request.name().trim();
        if (projectRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Project name already exists");
        }

        String plaintextKey = generateUniqueApiKey();
        Project project = Project.builder()
                .name(name)
                .apiKeyHash(ProjectApiKeyGenerator.hash(plaintextKey))
                .apiKeyPrefix(ProjectApiKeyGenerator.prefix(plaintextKey))
                .webhookUrl(normalizeWebhookUrl(request.webhookUrl()))
                .isActive(Boolean.TRUE)
                .build();
        Project saved = projectRepository.save(project);

        AlertRule defaultRule = AlertRule.builder()
                .project(saved)
                .minRiskScore(Math.max(0, globalRiskThreshold))
                .watchlistOnly(Boolean.FALSE)
                .contractCreationOnly(Boolean.FALSE)
                .enabled(Boolean.TRUE)
                .build();
        alertRuleRepository.save(defaultRule);
        refreshCaches();

        return toDto(saved, plaintextKey);
    }

    @Transactional
    public Optional<ProjectDTO> update(Long id, ProjectUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Optional<Project> existingOpt = projectRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        Project existing = existingOpt.get();

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(existing.getName()) && projectRepository.existsByNameIgnoreCase(name)) {
                throw new IllegalArgumentException("Project name already exists");
            }
            existing.setName(name);
        }
        if (request.webhookUrl() != null) {
            existing.setWebhookUrl(normalizeWebhookUrl(request.webhookUrl()));
        }
        if (request.isActive() != null) {
            existing.setIsActive(request.isActive());
        }

        Project saved = projectRepository.save(existing);
        refreshCaches();
        return Optional.of(toDto(saved, null));
    }

    @Transactional
    public Optional<ProjectDTO> rotateApiKey(Long id) {
        Optional<Project> existingOpt = projectRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        Project existing = existingOpt.get();
        String plaintextKey = generateUniqueApiKey();
        existing.setApiKeyHash(ProjectApiKeyGenerator.hash(plaintextKey));
        existing.setApiKeyPrefix(ProjectApiKeyGenerator.prefix(plaintextKey));
        Project saved = projectRepository.save(existing);
        return Optional.of(toDto(saved, plaintextKey));
    }

    private String generateUniqueApiKey() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = ProjectApiKeyGenerator.generate();
            if (!projectRepository.existsByApiKeyHash(ProjectApiKeyGenerator.hash(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique project API key");
    }

    private void refreshCaches() {
        watchlistCacheService.refresh();
        alertRuleCacheService.refresh();
    }

    private ProjectDTO toDto(Project project, String plaintextApiKey) {
        String apiKey = plaintextApiKey != null
                ? plaintextApiKey
                : ProjectApiKeyGenerator.maskFromPrefix(project.getApiKeyPrefix());
        return new ProjectDTO(
                project.getId(),
                project.getName(),
                apiKey,
                project.getWebhookUrl(),
                project.getIsActive(),
                project.getCreatedAt()
        );
    }

    private void validateCreateRequest(ProjectCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
    }

    private String normalizeWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return null;
        }
        return webhookUrl.trim();
    }
}
