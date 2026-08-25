package com.lucentflow.api.dto;

/**
 * Request payload for creating a project.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record ProjectCreateRequest(
        String name,
        String webhookUrl,
        String webhookSecret,
        String plan,
        Integer dailyRequestQuota,
        Integer watchlistLimit
) {
}
