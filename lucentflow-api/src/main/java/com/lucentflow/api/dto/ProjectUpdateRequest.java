package com.lucentflow.api.dto;

/**
 * Request payload for updating a project.
 * Blank {@code webhookSecret} clears the project secret (fall back to global).
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ProjectUpdateRequest(
        String name,
        String webhookUrl,
        String webhookSecret,
        Boolean isActive
) {
}
