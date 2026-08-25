package com.lucentflow.analyzer.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Alert metadata shared across outbound providers.
 *
 * @author ArchLucent
 * @since 1.0
 */
public record AlertDispatchContext(
        boolean watchlistHit,
        String watchlistLabel,
        String watchlistCategory,
        String watchlistAddress,
        Long projectId,
        String projectWebhookUrl,
        String projectWebhookSecret
) {

    /**
     * Collapse per-project contexts into one payload for operator-global channels
     * (Discord / Telegram). Prefers watchlist hits so a later tenant hit is not hidden
     * behind a score-only first context.
     *
     * @param contexts non-empty dispatch list
     * @return a single context; watchlist labels from distinct hits are joined
     */
    public static AlertDispatchContext mergeForGlobalChannel(List<AlertDispatchContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            throw new IllegalArgumentException("contexts must not be empty");
        }
        if (contexts.size() == 1) {
            return contexts.getFirst();
        }
        List<AlertDispatchContext> hits = contexts.stream()
                .filter(AlertDispatchContext::watchlistHit)
                .toList();
        AlertDispatchContext representative = hits.isEmpty() ? contexts.getFirst() : hits.getFirst();
        String labels = hits.stream()
                .map(AlertDispatchContext::watchlistLabel)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        return new AlertDispatchContext(
                !hits.isEmpty(),
                labels.isEmpty() ? representative.watchlistLabel() : labels,
                representative.watchlistCategory(),
                representative.watchlistAddress(),
                representative.projectId(),
                representative.projectWebhookUrl(),
                representative.projectWebhookSecret()
        );
    }
}
