package com.lucentflow.api.dto;

/**
 * Request payload for updating a project.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ProjectUpdateRequest(
        String name,
        String webhookUrl,
        Boolean isActive
) {
}
